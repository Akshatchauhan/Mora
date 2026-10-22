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
 * 4. Exact-second deadline timer callback for immediate deadline interception.
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
    private var deadlineCheckRunnable: Runnable? = null

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
                    scheduleExactDeadlineCheck(activeTasks)
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
        val mindfulPause = prefs.isMindfulPauseEnabled
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

        // Skip transient / keyboard / launcher windows
        if (fgPkg == packageName ||
            launcherPackages.contains(fgPkg) ||
            fgPkg.contains("inputmethod", ignoreCase = true) ||
            fgPkg.contains("keyboard", ignoreCase = true)
        ) {
            return
        }

        val elapsedMs = SystemClock.elapsedRealtime()
        val nowWallMs = System.currentTimeMillis()
        currentForegroundPackage = fgPkg

        val focusDecision: FocusLockDecision? = if (
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
                if (previousState != nextState || overlayView == null) {
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
        val isLowRam = PermissionHelper.isLowRamDevice(this)
        val hasOverlay = PermissionHelper.isOverlayPermissionGranted(this)

        if (pauseState.isFocusLock || !hasOverlay || isLowRam) {
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
            startActivity(intent)
        } else {
            // Lightweight custom-Canvas overlay with organic lung swell haptics
            showOverlayForState(pauseState)
        }
    }

    private fun showOverlayForState(pauseState: MindfulPauseState.PauseVisible) {
        removeOverlaySafely()

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val prefs = OnboardingPreferences(applicationContext)
        val themeMode = prefs.themeMode
        val isDark = when (themeMode) {
            AppThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        }
        val isAmoled = themeMode == AppThemeMode.AMOLED
        val compactHeight = resources.configuration.screenHeightDp < 480

        val bgColor = when {
            isAmoled -> Color.BLACK
            isDark -> Color.parseColor("#121A15")
            else -> Color.parseColor("#F7F7F1")
        }
        val surfaceColor = when {
            isAmoled -> Color.parseColor("#0D1310")
            isDark -> Color.parseColor("#1B261F")
            else -> Color.parseColor("#FFFFFF")
        }
        val primaryColor = if (isDark) Color.parseColor("#9CD2B0") else Color.parseColor("#315C47")
        val onPrimaryColor = if (isDark) Color.parseColor("#121A15") else Color.parseColor("#FFFFFF")
        val textPrimaryColor = if (isDark) Color.parseColor("#ECF3ED") else Color.parseColor("#21362C")
        val textSecondaryColor = if (isDark) Color.parseColor("#B0C2B5") else Color.parseColor("#667269")

        val appLabel = resolveAppLabel(pauseState.targetPackage)
        val nudge = MoraNudgeEngine.buildPauseNudge(
            appLabel = appLabel,
            activeTasks = qualifyingFocusLockTasks,
            attemptCountToday = currentBlockedAttemptCount,
            fallbackPrompt = pauseState.promptText
        )

        val rootLayout = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    navigateToHomeScreen()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(bgColor)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = dp(24)
            setPadding(pad, pad, pad, pad)
        }

        // Title
        val titleView = TextView(this).apply {
            text = "Pause before $appLabel"
            setTextColor(textPrimaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compactHeight) 22f else 26f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        container.addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Badge if present
        if (!nudge.badgeText.isNullOrBlank()) {
            val badgeView = TextView(this).apply {
                text = nudge.badgeText
                setTextColor(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (isDark) Color.parseColor("#1F332C") else Color.parseColor("#E0EFEA"))
                }
            }
            val badgeParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            container.addView(badgeView, badgeParams)
        }

        // Subtext / Nudge Headline (avoiding duplicate "Pause before $appLabel")
        val subtextView = TextView(this).apply {
            text = if (nudge.headline.equals("Pause before $appLabel", ignoreCase = true)) {
                nudge.subtext
            } else {
                nudge.headline
            }
            setTextColor(textSecondaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        container.addView(subtextView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Breathing Circle Canvas View
        val breathingCircle = BreathingCircleView(this, isDark, primaryColor, onPrimaryColor).apply {
            val size = dp(if (compactHeight) 140 else 200)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dp(20)
                bottomMargin = dp(20)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(breathingCircle)

        // Action Buttons
        val continueButton = Button(this).apply {
            text = "Breathe in..."
            isEnabled = false
            alpha = 0.6f
            setTextColor(onPrimaryColor)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(primaryColor)
            }
            setOnClickListener {
                stateMachine.onContinueClicked(SystemClock.elapsedRealtime())
                removeOverlaySafely()
            }
        }
        val btnParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(12)
        }
        container.addView(continueButton, btnParams)

        val leaveButton = Button(this).apply {
            text = "Back to home"
            setTextColor(primaryColor)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1), primaryColor)
            }
            setOnClickListener {
                stateMachine.onLeaveClicked()
                removeOverlaySafely()
                navigateToHomeScreen()
            }
        }
        val leaveParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(8)
        }
        container.addView(leaveButton, leaveParams)

        scroll.addView(container)
        rootLayout.addView(scroll)

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
        }

        runCatching {
            wm.addView(rootLayout, layoutParams)
            overlayView = rootLayout
            startBreathingAnimation(pauseState.targetPackage, pauseState.durationSeconds, breathingCircle, continueButton, appLabel)
        }
    }

    private fun startBreathingAnimation(
        targetPackage: String,
        totalDurationSeconds: Int,
        breathingCircle: BreathingCircleView,
        continueButton: Button,
        appLabel: String
    ) {
        breathingAnimator?.cancel()
        haptics.resetBreathCycle()
        PausePing.playIfEnabled(applicationContext)

        val prefs = OnboardingPreferences(applicationContext)
        val cycles = prefs.getPauseCyclesForPackage(targetPackage).coerceIn(1, 4)
        val effectiveDurationSeconds = OnboardingPreferences.cyclesToDurationSeconds(cycles)
        val cycleDurationMs = (effectiveDurationSeconds * 1000L) / cycles
        val halfCycleMs = cycleDurationMs / 2

        continueButton.alpha = 0.6f
        continueButton.isEnabled = false
        continueButton.text = "Breathe in..."

        val cycleEasing = AccelerateDecelerateInterpolator()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = effectiveDurationSeconds * 1000L
            interpolator = LinearInterpolator() // Uniform timing across all breath cycles
            addUpdateListener { anim ->
                val totalProgress = anim.animatedValue as Float
                val elapsedMs = (totalProgress * duration).toLong()
                val currentCycleTime = elapsedMs % cycleDurationMs
                val isInhale = currentCycleTime < halfCycleMs
                val rawRatio = if (isInhale) {
                    currentCycleTime.toFloat() / halfCycleMs
                } else {
                    (currentCycleTime - halfCycleMs).toFloat() / halfCycleMs
                }.coerceIn(0f, 1f)
                val phaseProgress = cycleEasing.getInterpolation(rawRatio)

                breathingCircle.updateState(phaseProgress, isInhale)
                haptics.onBreathingFrame(phaseProgress, isInhale)

                continueButton.text = if (isInhale) "Breathe in..." else "Breathe out..."
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    haptics.onBreathingCycleComplete()
                    breathingCircle.updateState(1f, false)
                    continueButton.isEnabled = true
                    continueButton.text = "Continue to $appLabel"
                    continueButton.animate().alpha(1f).setDuration(250).start()
                }
            })
        }
        breathingAnimator = animator
        animator.start()
    }

    private fun removeOverlaySafely() {
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

    private fun scheduleExactDeadlineCheck(tasks: List<Task>) {
        deadlineCheckRunnable?.let(mainHandler::removeCallbacks)
        deadlineCheckRunnable = null
        val now = System.currentTimeMillis()
        val nextDeadline = tasks.asSequence()
            .filter { it.status == TaskStatus.PENDING && it.deadlineTimestamp > now }
            .minOfOrNull { it.deadlineTimestamp } ?: return
        val runnable = Runnable {
            val app = applicationContext as? DueOrDieApplication ?: return@Runnable
            serviceScope.launch(Dispatchers.IO) {
                val nowTime = System.currentTimeMillis()
                app.taskRepository.checkAndUpdateOverdueTasks(nowTime)
                val prefs = OnboardingPreferences(applicationContext)
                if (prefs.isOverdueAlarmEnabled) {
                    val allTasks = app.taskRepository.allTasks.first()
                    val overdueTasks = allTasks.filter { it.isOverdueAt(nowTime) && it.status != TaskStatus.COMPLETED }
                    for (task in overdueTasks) {
                        NotificationHelper.showOverdueAlarmNotification(applicationContext, task)
                    }
                }
                mainHandler.post {
                    checkForegroundApp()
                }
            }
        }
        deadlineCheckRunnable = runnable
        mainHandler.postDelayed(runnable, (nextDeadline - now + 200L).coerceAtLeast(200L))
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
            isAccessibilityServiceEnabled = true,
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
        deadlineCheckRunnable?.let(mainHandler::removeCallbacks)
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

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private class BreathingCircleView(
        context: Context,
        private val isDark: Boolean,
        private val primaryColor: Int,
        private val onPrimaryColor: Int
    ) : View(context) {

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = if (isDark) Color.parseColor("#3A5040") else Color.parseColor("#DEE4DA")
        }
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.parseColor("#24352A") else Color.parseColor("#EAF0E7")
        }
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryColor
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryColor
            textAlign = Paint.Align.CENTER
            textSize = 38f
            isFakeBoldText = true
        }

        private var progress = 0f
        private var isInhale = true

        fun updateState(phaseProgress: Float, inhale: Boolean) {
            progress = phaseProgress
            isInhale = inhale
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val maxR = (minOf(cx, cy) - 16f).coerceAtLeast(10f)
            val minR = maxR * 0.45f

            // Outer subtle boundary ring
            canvas.drawCircle(cx, cy, maxR, ringPaint)

            // Soft soothing halo container
            canvas.drawCircle(cx, cy, maxR - 8f, haloPaint)

            // Dynamic lung-filling air circle
            val currentR = if (isInhale) {
                minR + (maxR - 16f - minR) * progress
            } else {
                (maxR - 16f) - (maxR - 16f - minR) * progress
            }
            canvas.drawCircle(cx, cy, currentR, circlePaint)

            val text = if (isInhale) "Breathe in" else "Breathe out"
            val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(text, cx, textY, textPaint)
        }
    }

    internal fun handleForegroundAppDetected(pkg: String) {
        serviceScope.launch(Dispatchers.Default) {
            checkForegroundApp(pkg)
        }
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.dueordie.action.START_OR_UPDATE_ONGOING"
        const val ACTION_CANCEL = "com.example.dueordie.action.CANCEL_ONGOING"

        @Volatile
        private var instance: OngoingNotificationService? = null

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
