package com.example.dueordie.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val storageValue: String, val displayLabel: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}

class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_SCHEMA_VERSION)) {
            prefs.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply()
        }
        syncThemeFlow(themeMode)
    }

    val schemaVersion: Int
        get() = prefs.getInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var themeMode: AppThemeMode
        get() = AppThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.storageValue))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.storageValue).apply()
            syncThemeFlow(value)
        }

    var isPausePingEnabled: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_PING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PAUSE_PING_ENABLED, value).apply()

    var areRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, value).apply()

    var isPersistentNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIFICATION_ENABLED, value).apply()

    var isHiddenTaskDeletionEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN_TASK_DELETION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDDEN_TASK_DELETION_ENABLED, value).apply()

    var isOverdueAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERDUE_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERDUE_ALARM_ENABLED, value).apply()

    var isUsageTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_USAGE_TRACKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_USAGE_TRACKING_ENABLED, value).apply()

    var isGlobalHostageModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOSTAGE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_HOSTAGE_MODE, value).apply()

    var isFocusLockEnabled: Boolean
        get() = isGlobalHostageModeEnabled
        set(value) {
            isGlobalHostageModeEnabled = value
        }

    var overdueCooldownUntilWallMs: Long
        get() = prefs.getLong(KEY_OVERDUE_COOLDOWN_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_OVERDUE_COOLDOWN_UNTIL, value.coerceAtLeast(0L)).apply()

    var lastCooldownTaskTitle: String
        get() = prefs.getString(KEY_COOLDOWN_TASK_TITLE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_COOLDOWN_TASK_TITLE, value.take(120)).apply()

    fun isInOverdueCooldown(nowMs: Long = System.currentTimeMillis()): Boolean = overdueCooldownUntilWallMs > nowMs

    fun remainingCooldownMinutes(nowMs: Long = System.currentTimeMillis()): Int {
        val diff = overdueCooldownUntilWallMs - nowMs
        if (diff <= 0L) return 0
        return ((diff + 59_999L) / 60_000L).toInt()
    }

    fun getPauseCyclesForPackage(packageName: String): Int {
        val key = KEY_PACKAGE_CYCLES_PREFIX + packageName.take(180)
        return prefs.getInt(key, DEFAULT_PAUSE_CYCLES).coerceIn(1, 4)
    }

    fun setPauseCyclesForPackage(packageName: String, cycles: Int) {
        val key = KEY_PACKAGE_CYCLES_PREFIX + packageName.take(180)
        prefs.edit().putInt(key, cycles.coerceIn(1, 4)).apply()
    }

    fun getPauseDurationSecondsForPackage(packageName: String): Int {
        val cycles = getPauseCyclesForPackage(packageName)
        return cyclesToDurationSeconds(cycles)
    }

    fun getAllCustomPackageCycles(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (pkg in pauseSelectedPackages) {
            val key = KEY_PACKAGE_CYCLES_PREFIX + pkg
            if (prefs.contains(key)) {
                map[pkg] = prefs.getInt(key, DEFAULT_PAUSE_CYCLES).coerceIn(1, 4)
            }
        }
        return map
    }

    fun getAppDailyLimitMinutes(packageName: String): Int {
        val key = KEY_APP_DAILY_LIMIT_MINUTES_PREFIX + packageName.take(180)
        return prefs.getInt(key, 0)
    }

    fun setAppDailyLimitMinutes(packageName: String, minutes: Int) {
        val key = KEY_APP_DAILY_LIMIT_MINUTES_PREFIX + packageName.take(180)
        if (minutes <= 0) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putInt(key, minutes).apply()
        }
    }

    fun getAllCustomDailyLimits(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (pkg in pauseSelectedPackages) {
            val key = KEY_APP_DAILY_LIMIT_MINUTES_PREFIX + pkg
            if (prefs.contains(key)) {
                val mins = prefs.getInt(key, 0)
                if (mins > 0) map[pkg] = mins
            }
        }
        return map
    }

    fun isAppPauseEnabled(packageName: String): Boolean {
        return pauseSelectedPackages.contains(packageName)
    }

    fun setAppPauseEnabled(packageName: String, enabled: Boolean) {
        val current = pauseSelectedPackages.toMutableSet()
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        pauseSelectedPackages = current
    }

    fun recordBlockedAttemptAndGetCount(packageName: String, dayKey: String = java.time.LocalDate.now().toString()): Int {
        val key = KEY_ATTEMPT_PREFIX + packageName.take(180)
        val storedDay = prefs.getString(KEY_ATTEMPT_DAY, "")
        val count = if (storedDay != dayKey) 1 else prefs.getInt(key, 0) + 1
        prefs.edit().putString(KEY_ATTEMPT_DAY, dayKey).putInt(key, count).apply()
        return count
    }

    var blockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_PACKAGES, null) ?: defaultBlockedPackages
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, value).apply()

    // Mindful Pause ("Pause before apps") settings
    var isMindfulPauseEnabled: Boolean
        get() = prefs.getBoolean(KEY_MINDFUL_PAUSE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MINDFUL_PAUSE_ENABLED, value).apply()

    var hasAcceptedAccessibilityDisclosure: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, value).apply()

    var pauseSelectedPackages: Set<String>
        get() = prefs.getStringSet(KEY_PAUSE_SELECTED_PACKAGES, null) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PAUSE_SELECTED_PACKAGES, value).apply()

    var pauseDurationSeconds: Int
        get() = prefs.getInt(KEY_PAUSE_DURATION_SECONDS, DEFAULT_PAUSE_DURATION_SECONDS)
            .coerceIn(MIN_PAUSE_DURATION_SECONDS, MAX_PAUSE_DURATION_SECONDS)
        set(value) = prefs.edit()
            .putInt(KEY_PAUSE_DURATION_SECONDS, value.coerceIn(MIN_PAUSE_DURATION_SECONDS, MAX_PAUSE_DURATION_SECONDS))
            .apply()

    /**
     * Stores the user's 1-line pause prompt, or `""` when the user selects "No phrase".
     */
    var pausePromptText: String
        get() = if (prefs.contains(KEY_PAUSE_PROMPT_TEXT)) {
            (prefs.getString(KEY_PAUSE_PROMPT_TEXT, DEFAULT_PAUSE_PROMPT) ?: "")
                .let { if (it == "Take one slow breath before continuing.") DEFAULT_PAUSE_PROMPT else it }
        } else {
            DEFAULT_PAUSE_PROMPT
        }
        set(value) = prefs.edit()
            .putString(KEY_PAUSE_PROMPT_TEXT, value.trim().take(80))
            .apply()

    var isReInterventionEnabled: Boolean
        get() = prefs.getBoolean(KEY_REINTERVENTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_REINTERVENTION_ENABLED, value).apply()

    var reInterventionIntervalMinutes: Int
        get() = prefs.getInt(KEY_REINTERVENTION_INTERVAL_MINUTES, DEFAULT_REINTERVENTION_MINUTES)
            .coerceIn(1, 120)
        set(value) = prefs.edit()
            .putInt(KEY_REINTERVENTION_INTERVAL_MINUTES, value.coerceIn(1, 120))
            .apply()

    var disabledReInterventionPackages: Set<String>
        get() = prefs.getStringSet(KEY_DISABLED_REINTERVENTION_PACKAGES, null) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DISABLED_REINTERVENTION_PACKAGES, value).apply()

    var returnGraceSeconds: Int
        get() = prefs.getInt(KEY_RETURN_GRACE_SECONDS, DEFAULT_RETURN_GRACE_SECONDS)
            .coerceIn(0, 600)
        set(value) = prefs.edit()
            .putInt(KEY_RETURN_GRACE_SECONDS, value.coerceIn(0, 600))
            .apply()

    // Tutorials & contextual guidance
    var showTutorialHints: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TUTORIAL_HINTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TUTORIAL_HINTS, value).apply()

    var hasCompletedTasksTour: Boolean
        get() = prefs.getInt(KEY_COMPLETED_TOUR_VERSION, 0) >= CURRENT_TUTORIAL_VERSION
        set(value) = prefs.edit()
            .putInt(KEY_COMPLETED_TOUR_VERSION, if (value) CURRENT_TUTORIAL_VERSION else 0)
            .apply()

    var tasksTourStep: Int
        get() = prefs.getInt(KEY_TASKS_TOUR_STEP, 0).coerceIn(0, 2)
        set(value) = prefs.edit().putInt(KEY_TASKS_TOUR_STEP, value.coerceIn(0, 2)).apply()

    var hasSeenPauseSetupTip: Boolean
        get() = prefs.getBoolean(KEY_SEEN_PAUSE_SETUP_TIP, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_PAUSE_SETUP_TIP, value).apply()

    var hasSeenAppPickerTip: Boolean
        get() = prefs.getBoolean(KEY_SEEN_APP_PICKER_TIP, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_APP_PICKER_TIP, value).apply()

    fun resetTutorials() {
        prefs.edit()
            .putBoolean(KEY_SHOW_TUTORIAL_HINTS, true)
            .putInt(KEY_COMPLETED_TOUR_VERSION, 0)
            .putInt(KEY_TASKS_TOUR_STEP, 0)
            .putBoolean(KEY_SEEN_PAUSE_SETUP_TIP, false)
            .putBoolean(KEY_SEEN_APP_PICKER_TIP, false)
            .apply()
    }

    fun isReInterventionEnabledForPackage(packageName: String): Boolean {
        return isReInterventionEnabled && !disabledReInterventionPackages.contains(packageName)
    }

    companion object {
        private const val PREFS_NAME = "due_or_die_onboarding_prefs"
        private const val KEY_SCHEMA_VERSION = "key_schema_version"
        private const val CURRENT_SCHEMA_VERSION = 3
        const val CURRENT_TUTORIAL_VERSION = 1

        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_PAUSE_PING_ENABLED = "key_pause_ping_enabled"
        private const val KEY_REMINDERS_ENABLED = "key_reminders_enabled"
        private const val KEY_PERSISTENT_NOTIFICATION_ENABLED = "key_persistent_notification_enabled"
        private const val KEY_HIDDEN_TASK_DELETION_ENABLED = "key_hidden_task_deletion_enabled"
        private const val KEY_OVERDUE_ALARM_ENABLED = "key_overdue_alarm_enabled"
        private const val KEY_USAGE_TRACKING_ENABLED = "key_usage_tracking_enabled"
        private const val KEY_HOSTAGE_MODE = "key_hostage_mode"
        private const val KEY_BLOCKED_PACKAGES = "key_blocked_packages"
        private const val KEY_OVERDUE_COOLDOWN_UNTIL = "key_overdue_cooldown_until"
        private const val KEY_COOLDOWN_TASK_TITLE = "key_cooldown_task_title"
        private const val KEY_ATTEMPT_DAY = "key_blocked_attempt_day"
        private const val KEY_ATTEMPT_PREFIX = "key_blocked_attempt_"
        private const val KEY_PACKAGE_CYCLES_PREFIX = "key_pkg_cycles_"
        private const val KEY_APP_DAILY_LIMIT_MINUTES_PREFIX = "key_app_limit_min_"

        private const val KEY_MINDFUL_PAUSE_ENABLED = "key_mindful_pause_enabled"
        private const val KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "key_accessibility_disclosure_accepted"
        private const val KEY_PAUSE_SELECTED_PACKAGES = "key_pause_selected_packages"
        private const val KEY_PAUSE_DURATION_SECONDS = "key_pause_duration_seconds"
        private const val KEY_PAUSE_PROMPT_TEXT = "key_pause_prompt_text"
        private const val KEY_REINTERVENTION_ENABLED = "key_reintervention_enabled"
        private const val KEY_REINTERVENTION_INTERVAL_MINUTES = "key_reintervention_interval_minutes"
        private const val KEY_DISABLED_REINTERVENTION_PACKAGES = "key_disabled_reintervention_packages"
        private const val KEY_RETURN_GRACE_SECONDS = "key_return_grace_seconds"

        private const val KEY_SHOW_TUTORIAL_HINTS = "key_show_tutorial_hints"
        private const val KEY_COMPLETED_TOUR_VERSION = "key_completed_tour_version"
        private const val KEY_TASKS_TOUR_STEP = "key_tasks_tour_step"
        private const val KEY_SEEN_PAUSE_SETUP_TIP = "key_seen_pause_setup_tip"
        private const val KEY_SEEN_APP_PICKER_TIP = "key_seen_app_picker_tip"

        const val DEFAULT_PAUSE_CYCLES = 2
        const val DEFAULT_PAUSE_DURATION_SECONDS = 8
        const val MIN_PAUSE_DURATION_SECONDS = 4
        const val MAX_PAUSE_DURATION_SECONDS = 30
        const val DEFAULT_PAUSE_PROMPT = "Take two slow breaths before continuing."
        const val DEFAULT_REINTERVENTION_MINUTES = 10
        const val DEFAULT_RETURN_GRACE_SECONDS = 30
        const val OVERDUE_UNBLOCK_COOLDOWN_MS = 10 * 60 * 1000L

        fun cyclesToDurationSeconds(cycles: Int): Int = when (cycles.coerceIn(1, 4)) {
            1 -> 5  // 1 cycle (2.5s in, 2.5s out) - Quick reset (impulse)
            2 -> 10 // 2 cycles (5s in, 5s out) - Mindful pause (standard)
            3 -> 16 // 3 cycles (8s in, 8s out) - Deep reset
            4 -> 24 // 4 cycles (12s in, 12s out) - High persistence
            else -> 10
        }

        fun durationSecondsToCycles(seconds: Int): Int = when {
            seconds <= 6 -> 1
            seconds <= 12 -> 2
            seconds <= 18 -> 3
            else -> 4
        }

        val TRAVEL_WHITELIST_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.ubercab",
            "com.olacabs.customer",
            "com.rapido.passenger",
            "com.google.android.apps.walletnfcrel",
            "com.makemytrip",
            "com.irctc.air",
            "cris.org.in.prs.ima",
            "com.booking",
            "com.airbnb.android"
        )

        fun isTravelEssentialPackage(packageName: String): Boolean {
            return TRAVEL_WHITELIST_PACKAGES.contains(packageName) ||
                packageName.contains("maps", ignoreCase = true) ||
                packageName.contains("uber", ignoreCase = true) ||
                packageName.contains("dialer", ignoreCase = true)
        }

        const val EXAMPLE_TASK_PREFIX = "[Example] "

        val PAUSE_PROMPT_PRESETS = listOf(
            DEFAULT_PAUSE_PROMPT,
            "Pause for a moment.",
            "Open with intention.",
            "" // No phrase
        )

        val PAUSE_DURATION_OPTIONS = listOf(8, 12, 20)
        val REINTERVENTION_MINUTE_OPTIONS = listOf(5, 10, 15, 30)
        val RETURN_GRACE_SECOND_OPTIONS = listOf(0, 15, 30, 60, 120)

        val defaultBlockedPackages = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.facebook.katana",
            "com.android.chrome"
        )

        private val _themeModeFlow = MutableStateFlow(AppThemeMode.SYSTEM)
        val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()

        private fun syncThemeFlow(mode: AppThemeMode) {
            _themeModeFlow.value = mode
        }
    }
}
