package com.example.dueordie.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.dueordie.ui.permissions.DynamicMindfulPauseContent
import com.example.dueordie.ui.theme.DueOrDieTheme
import com.example.dueordie.util.AppInteractionMode
import com.example.dueordie.util.OverlayLifecycleOwner
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.FocusLockDecision
import com.example.dueordie.data.model.MindfulPauseConfig
import com.example.dueordie.data.model.MindfulPauseState
import com.example.dueordie.data.model.MindfulPauseStateMachine
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.ui.hostage.HostageBlockActivity
import com.example.dueordie.util.AppThemeMode
import com.example.dueordie.util.MoraHaptics
import com.example.dueordie.util.MoraNudgeEngine
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PausePing
import com.example.dueordie.util.PermissionHelper
import com.example.dueordie.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Unified foreground status and interception monitor for Mora.
 *
 * Operates without requiring the Accessibility Service (preventing Google Play Protect warnings):
 * 1. Maintains the persistent task status notification in the notification drawer.
 * 2. Runs the foreground app polling loop via UsageStatsHelper and MindfulPauseStateMachine.
 * 3. Dual-mode interception:
 *    - Primary: Custom Canvas breathing overlay window (TYPE_APPLICATION_OVERLAY) with organic lung swell haptics.
 *    - Fallback / Focus Lock: Fullscreen HostageBlockActivity (singleInstance, zero-bypass).
 * 4. Schedules deadline alarms that remain in Android's alarm service if this process stops.
 * 5. Self-healing watchdog in onTaskRemoved().
 */
class OngoingNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val haptics by lazy { MoraHaptics(applicationContext) }
    private val stateMachine = MindfulPauseStateMachine()

    private var observeJob: Job? = null
    private var monitorJob: Job? = null
    private var qualifyingFocusLockTasks: List<Task> = emptyList()
    private var launcherPackages: Set<String> = emptySet()
    private var currentForegroundPackage: String? = null
    private var currentBlockedAttemptCount = 0

    private var overlayView: View? = null
    private var breathingAnimator: ValueAnimator? = null
    private var reInterventionRunnable: Runnable? = null
    private var pauseActivityFallbackRunnable: Runnable? = null
    private var presentedPauseTargetPackage: String? = null
    private var pauseActivityVisible = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                stateMachine.onScreenOffOrServiceStopped()
                cancelReInterventionTimer()
                removeOverlaySafely()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundSafely(NotificationHelper.buildOngoingNotification(this, emptyList()))
        refreshLauncherPackages()
        syncConfigFromPreferences()
        runCatching {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_OR_UPDATE
        if (action == ACTION_CANCEL) {
            stopAndCleanup()
            return START_NOT_STICKY
        }
        if (action == ACTION_CONTINUE_CLICKED) {
            stateMachine.onContinueClicked(SystemClock.elapsedRealtime())
            removeOverlaySafely()
            return START_STICKY
        }
        if (action == ACTION_LEAVE_CLICKED) {
            stateMachine.onLeaveClicked()
            removeOverlaySafely()
            return START_STICKY
        }

        refreshLauncherPackages()
        syncConfigFromPreferences()
        startObservingTasks()
        startForegroundMonitor()
        return START_STICKY
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.ONGOING_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NotificationHelper.ONGOING_NOTIFICATION_ID,
                    notification
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun startObservingTasks() {
        observeJob?.cancel()
        val app = applicationContext as? DueOrDieApplication ?: return
        val repository = app.taskRepository
        observeJob = serviceScope.launch(Dispatchers.IO) {
            repository.allTasks.collectLatest { tasks ->
                val prefs = OnboardingPreferences(applicationContext)
                val activeTasks = tasks.filter { it.status != TaskStatus.COMPLETED }
                val nowMs = System.currentTimeMillis()

                qualifyingFocusLockTasks = activeTasks.filter { it.isHostageModeEnabled }

                if (prefs.isPersistentNotificationEnabled && activeTasks.isNotEmpty()) {
                    val notification = NotificationHelper.buildOngoingNotification(
                        applicationContext,
                        activeTasks
                    )
                    startForegroundSafely(notification)
                }

                launch(Dispatchers.Main) {
                    DeadlineAlarmScheduler.scheduleNext(
                        context = applicationContext,
                        tasks = activeTasks,
                        enabled = prefs.isOverdueAlarmEnabled
                    )
                    syncConfigFromPreferences()

                    if (!shouldRunService(prefs, activeTasks, nowMs)) {
                        stopAndCleanup()
                    }
                }
            }
        }
    }

    private fun shouldRunService(
        prefs: OnboardingPreferences,
        activeTasks: List<Task>,
        nowMs: Long
    ): Boolean {
        val persistentNotification = prefs.isPersistentNotificationEnabled && activeTasks.isNotEmpty()
        val mindfulPause = prefs.isMindfulPauseEnabled &&
            PermissionHelper.isPauseAndFocusAccessGranted(this)
        val focusLock = prefs.isGlobalHostageModeEnabled &&
            (TaskDomainRules.hasQualifyingFocusLockTasks(activeTasks, nowMs) || prefs.isInOverdueCooldown(nowMs))
        return persistentNotification || mindfulPause || focusLock
    }

    private fun startForegroundMonitor() {
        monitorJob?.cancel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        monitorJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val isInteractive = powerManager?.isInteractive ?: true
                if (isInteractive) {
                    checkForegroundApp()
                }
                delay(750L)
            }
        }
    }

    private fun checkForegroundApp(explicitPackage: String? = null) {
        val prefs = OnboardingPreferences(applicationContext)
        val fgPkg = explicitPackage ?: UsageStatsHelper.getCurrentlyForegroundPackage(applicationContext) ?: return

        // Ignore our own UI and keyboard windows, but process launcher transitions so
        // the state machine can close a pause and start the configured return grace.
        if (fgPkg == packageName ||
            fgPkg.contains("inputmethod", ignoreCase = true) ||
            fgPkg.contains("keyboard", ignoreCase = true)
        ) {
            return
        }

        val elapsedMs = SystemClock.elapsedRealtime()
        val nowWallMs = System.currentTimeMillis()
        currentForegroundPackage = fgPkg

        val focusDecision: FocusLockDecision? = if (
            !launcherPackages.contains(fgPkg) &&
            TaskDomainRules.shouldRunFocusLockMonitor(
                isGlobalFocusLockEnabled = prefs.isGlobalHostageModeEnabled,
                hasPermission = true,
                tasks = qualifyingFocusLockTasks,
                nowMs = nowWallMs,
                cooldownUntilMs = prefs.overdueCooldownUntilWallMs
            )
        ) {
            TaskDomainRules.evaluateFocusLock(
                isGlobalFocusLockEnabled = prefs.isGlobalHostageModeEnabled,
                hasUsagePermission = true,
                foregroundPackage = fgPkg,
                selfPackage = packageName,
                blockedPackages = prefs.blockedPackages,
                tasks = qualifyingFocusLockTasks,
                nowMs = nowWallMs,
                cooldownUntilMs = prefs.overdueCooldownUntilWallMs,
                cooldownTaskTitle = prefs.lastCooldownTaskTitle
            )
        } else {
            null
        }

        mainHandler.post {
            val previousState = stateMachine.state
            val nextState = stateMachine.onForegroundPackageChanged(
                packageName = fgPkg,
                elapsedRealtimeMs = elapsedMs,
                focusLockDecision = focusDecision
            )
            applyStateTransition(previousState, nextState)
        }
    }

    private fun applyStateTransition(
        previousState: MindfulPauseState,
        nextState: MindfulPauseState
    ) {
        when (nextState) {
            is MindfulPauseState.PauseVisible -> {
                cancelReInterventionTimer()
                if (previousState != nextState || presentedPauseTargetPackage != nextState.targetPackage) {
                    if (previousState != nextState) {
                        currentBlockedAttemptCount = OnboardingPreferences(applicationContext)
                            .recordBlockedAttemptAndGetCount(nextState.targetPackage)
                    }
                    dispatchIntervention(nextState)
                }
            }
            is MindfulPauseState.ActiveInTarget -> {
                removeOverlaySafely()
                scheduleReInterventionIfNeeded(nextState)
            }
            is MindfulPauseState.GraceWindow,
            is MindfulPauseState.Ready,
            is MindfulPauseState.Disabled -> {
                cancelReInterventionTimer()
                removeOverlaySafely()
            }
        }
    }

    private fun dispatchIntervention(pauseState: MindfulPauseState.PauseVisible) {
        removeOverlaySafely()

        if (pauseState.isFocusLock) {
            // Fullscreen zero-bypass HostageBlockActivity
            val appLabel = resolveAppLabel(pauseState.targetPackage)
            val taskTitle = pauseState.focusLockTaskTitle ?: "Overdue task"
            val nudge = if (pauseState.isFocusLock) {
                MoraNudgeEngine.buildFocusLockNudge(
                    appLabel = appLabel,
                    taskTitle = taskTitle,
                    overdueMinutes = pauseState.focusLockOverdueMinutes,
                    attemptCountToday = currentBlockedAttemptCount,
                    isCooldownActive = pauseState.isCooldownActive,
                    cooldownRemainingMinutes = pauseState.cooldownRemainingMinutes
                )
            } else {
                MoraNudgeEngine.buildPauseNudge(
                    appLabel = appLabel,
                    activeTasks = qualifyingFocusLockTasks,
                    attemptCountToday = currentBlockedAttemptCount,
                    fallbackPrompt = pauseState.promptText
                )
            }

            val intent = HostageBlockActivity.createIntent(
                context = this,
                taskTitle = taskTitle,
                overdueMinutes = pauseState.focusLockOverdueMinutes,
                isCooldown = pauseState.isCooldownActive,
                cooldownMinutes = pauseState.cooldownRemainingMinutes,
                nudgeHeadline = nudge.headline,
                nudgeSubtext = nudge.subtext,
                nudgeBadge = nudge.badgeText
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            presentedPauseTargetPackage = pauseState.targetPackage
            runCatching { startActivity(intent) }.onFailure {
                presentedPauseTargetPackage = null
            }
        } else {
            if (!PermissionHelper.isPauseAndFocusAccessGranted(this)) {
                // Access may have been revoked while the service was running. Do not
                // leave a gentle pause in a state that cannot be presented safely.
                syncConfigFromPreferences()
                removeOverlaySafely()
                return
            }
            showOverlayForState(pauseState)
        }
    }

    private var currentOverlayLifecycleOwner: OverlayLifecycleOwner? = null

    private fun showOverlayForState(pauseState: MindfulPauseState.PauseVisible) {
        removeOverlaySafely()

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        presentedPauseTargetPackage = pauseState.targetPackage

        val rootLayout = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    stateMachine.onLeaveClicked()
                    removeOverlaySafely()
                    navigateToHomeScreen()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val lifecycleOwner = OverlayLifecycleOwner()
        currentOverlayLifecycleOwner = lifecycleOwner

        val prefs = OnboardingPreferences(applicationContext)
        val appUsageMs = UsageStatsHelper.getAppUsageTodayMs(applicationContext, pauseState.targetPackage)
        val appMinutes = appUsageMs / 60_000L
        val chosenPackages = prefs.pauseSelectedPackages
        val totalChosenMinutes = chosenPackages.sumOf { pkg ->
            UsageStatsHelper.getAppUsageTodayMs(applicationContext, pkg) / 60_000L
        }.toInt()
        val limitMinutes = prefs.dailyChosenAppsLimitMinutes
        val isStrictDailyLimitReached = (prefs.appInteractionMode == AppInteractionMode.STRICT) && (totalChosenMinutes >= limitMinutes)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val themeMode by OnboardingPreferences.themeModeFlow.collectAsState()
                val isDark = when (themeMode) {
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK, AppThemeMode.AMOLED -> true
                }

                DueOrDieTheme(darkTheme = isDark, amoledBlack = themeMode == AppThemeMode.AMOLED) {
                    DynamicMindfulPauseContent(
                        targetPackage = pauseState.targetPackage,
                        promptText = pauseState.promptText,
                        durationSeconds = pauseState.durationSeconds,
                        isStrictDailyLimitReached = isStrictDailyLimitReached,
                        dailyScreenTimeMinutes = appMinutes,
                        dailyScreenTimeLimitMinutes = limitMinutes,
                        onContinueToApp = {
                            stateMachine.onContinueClicked(SystemClock.elapsedRealtime())
                            removeOverlaySafely()
                        },
                        onStepAwayToTasks = {
                            stateMachine.onLeaveClicked()
                            removeOverlaySafely()
                            navigateToHomeScreen()
                        },
                        onDismiss = {
                            stateMachine.onLeaveClicked()
                            removeOverlaySafely()
                            navigateToHomeScreen()
                        }
                    )
                }
            }
        }

        rootLayout.addView(composeView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching {
            wm.addView(rootLayout, layoutParams)
            overlayView = rootLayout
        }
    }

    private fun removeOverlaySafely() {
        pauseActivityFallbackRunnable?.let(mainHandler::removeCallbacks)
        pauseActivityFallbackRunnable = null
        presentedPauseTargetPackage = null
        pauseActivityVisible = false
        breathingAnimator?.cancel()
        breathingAnimator = null
        haptics.cancel()
        overlayView?.let { view ->
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(view) }
        }
        overlayView = null
        currentOverlayLifecycleOwner?.destroy()
        currentOverlayLifecycleOwner = null
    }

    private fun onPauseActivityVisible(targetPackage: String) {
        if (presentedPauseTargetPackage != targetPackage) return
        pauseActivityVisible = true
        pauseActivityFallbackRunnable?.let(mainHandler::removeCallbacks)
        pauseActivityFallbackRunnable = null

        // If Android delayed the activity past the fallback timeout, remove the
        // temporary window overlay now that the full-screen pause is visible.
        breathingAnimator?.cancel()
        breathingAnimator = null
        haptics.cancel()
        overlayView?.let { view ->
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(view) }
        }
        overlayView = null
    }

    private fun navigateToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun scheduleReInterventionIfNeeded(activeState: MindfulPauseState.ActiveInTarget) {
        cancelReInterventionTimer()
        val dueAtElapsedMs = activeState.nextReInterventionElapsedMs ?: return
        val delayMs = (dueAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(1000L)

        val runnable = Runnable {
            val nowElapsed = SystemClock.elapsedRealtime()
            val prev = stateMachine.state
            val updated = stateMachine.onReInterventionTimerTick(
                currentForegroundPackage = currentForegroundPackage,
                elapsedRealtimeMs = nowElapsed
            )
            applyStateTransition(prev, updated)
        }
        reInterventionRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelReInterventionTimer() {
        reInterventionRunnable?.let { mainHandler.removeCallbacks(it) }
        reInterventionRunnable = null
    }

    private fun refreshLauncherPackages() {
        launcherPackages = runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            packageManager.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun syncConfigFromPreferences(elapsedMs: Long = SystemClock.elapsedRealtime()) {
        val prefs = OnboardingPreferences(applicationContext)
        val ignored = MindfulPauseConfig.DEFAULT_IGNORED_PACKAGES + launcherPackages
        val newConfig = MindfulPauseConfig(
            isPauseEnabled = prefs.isMindfulPauseEnabled,
            isAccessibilityServiceEnabled = PermissionHelper.isPauseAndFocusAccessGranted(applicationContext),
            selectedPackages = prefs.pauseSelectedPackages,
            pauseDurationSeconds = prefs.pauseDurationSeconds,
            promptText = prefs.pausePromptText,
            isReInterventionEnabled = prefs.isReInterventionEnabled,
            reInterventionIntervalMinutes = prefs.reInterventionIntervalMinutes,
            disabledReInterventionPackages = prefs.disabledReInterventionPackages,
            returnGraceSeconds = prefs.returnGraceSeconds,
            selfPackage = packageName,
            ignoredPackages = ignored,
            packageDurationSeconds = prefs.getAllCustomPackageCycles().mapValues { OnboardingPreferences.cyclesToDurationSeconds(it.value) },
            packageReInterventionMinutes = prefs.getAllCustomDailyLimits()
        )
        stateMachine.updateConfig(newConfig, elapsedMs)
    }

    private fun resolveAppLabel(pkgName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkgName)
    }

    private fun stopAndCleanup() {
        observeJob?.cancel()
        monitorJob?.cancel()
        cancelReInterventionTimer()
        removeOverlaySafely()
        NotificationHelper.cancelOngoingNotification(applicationContext)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Self-healing watchdog: restart service via AlarmManager if swiped from recents
        runCatching {
            val restartIntent = Intent(applicationContext, OngoingNotificationService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                9921,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1500L,
                pendingIntent
            )
        }
        MoraKeepAliveListenerService.ensureRebound(applicationContext)
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        runCatching { unregisterReceiver(screenOffReceiver) }
        stopAndCleanup()
        serviceScope.cancel()
        super.onDestroy()
    }

    internal fun handleForegroundAppDetected(pkg: String) {
        serviceScope.launch(Dispatchers.Default) {
            checkForegroundApp(pkg)
        }
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.dueordie.action.START_OR_UPDATE_ONGOING"
        const val ACTION_CANCEL = "com.example.dueordie.action.CANCEL_ONGOING"
        const val ACTION_CONTINUE_CLICKED = "com.example.dueordie.action.CONTINUE_CLICKED"
        const val ACTION_LEAVE_CLICKED = "com.example.dueordie.action.LEAVE_CLICKED"
        private const val PAUSE_ACTIVITY_VISIBLE_TIMEOUT_MS = 2500L

        @Volatile
        private var instance: OngoingNotificationService? = null

        fun notifyContinueClicked(context: Context) {
            val intent = Intent(context, OngoingNotificationService::class.java).apply {
                action = ACTION_CONTINUE_CLICKED
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun notifyLeaveClicked(context: Context) {
            val intent = Intent(context, OngoingNotificationService::class.java).apply {
                action = ACTION_LEAVE_CLICKED
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun notifyPauseActivityVisible(targetPackage: String) {
            instance?.onPauseActivityVisible(targetPackage)
        }

        fun notifyForegroundAppChanged(context: Context, packageName: String) {
            val current = instance
            if (current != null) {
                current.handleForegroundAppDetected(packageName)
            } else {
                startOrUpdate(context)
            }
        }

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, OngoingNotificationService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            NotificationHelper.cancelOngoingNotification(context)
            val intent = Intent(context, OngoingNotificationService::class.java)
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
