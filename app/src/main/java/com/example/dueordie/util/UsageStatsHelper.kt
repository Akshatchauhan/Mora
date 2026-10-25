package com.example.dueordie.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val icon: Drawable? = null
) {
    val formattedTime: String
        get() {
            val totalSecs = usageTimeMs / 1000
            val totalMins = totalSecs / 60
            val hours = totalMins / 60
            val mins = totalMins % 60

            return when {
                hours > 0 && mins > 0 -> "$hours hr${if (hours > 1) "s" else ""} $mins min${if (mins > 1) "s" else ""}"
                hours > 0 -> "$hours hr${if (hours > 1) "s" else ""}"
                mins > 0 -> "$mins min${if (mins > 1) "s" else ""}"
                else -> "< 1 min"
            }
        }
}

data class SelectableAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSelected: Boolean,
    val usageTimeMs: Long
)

data class DailyUsageSummary(
    val totalScreenTimeMs: Long,
    val topApps: List<AppUsageInfo>
)

data class UsageEventRecord(
    val eventType: Int,
    val packageName: String,
    val timeStamp: Long
)

object UsageStatsHelper {

    fun getTodayUsageSummary(context: Context, limit: Int = 10): DailyUsageSummary {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return DailyUsageSummary(0L, emptyList())
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DailyUsageSummary(0L, emptyList())

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        if (now <= startOfDay) {
            return DailyUsageSummary(0L, emptyList())
        }

        // Query events from 3 hours prior to midnight to catch apps active across midnight
        val queryStart = maxOf(0L, startOfDay - 3 * 3600 * 1000L)
        val events = usageStatsManager.queryEvents(queryStart, now) ?: return DailyUsageSummary(0L, emptyList())

        val records = ArrayList<UsageEventRecord>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg != null) {
                records.add(UsageEventRecord(event.eventType, pkg, event.timeStamp))
            }
        }

        val packageManager = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val launcherPackages = try {
            packageManager.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val excludedPackages = setOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.launcher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "android",
            "com.google.android.gms"
        ) + launcherPackages

        val usageMap = processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = excludedPackages
        )

        // Only include apps that have a launch intent or are known user apps, with usage >= 1000ms
        val validApps = usageMap.entries
            .asSequence()
            .filter { entry ->
                entry.value >= 1000L &&
                        !excludedPackages.contains(entry.key) &&
                        try {
                            packageManager.getLaunchIntentForPackage(entry.key) != null
                        } catch (_: Exception) {
                            false
                        }
            }
            .sortedByDescending { it.value }
            .map { entry ->
                val pkgName = entry.key
                var label = pkgName
                var iconDrawable: Drawable? = null
                try {
                    val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                    label = packageManager.getApplicationLabel(appInfo).toString()
                    iconDrawable = packageManager.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                }
                AppUsageInfo(
                    packageName = pkgName,
                    appName = label,
                    usageTimeMs = entry.value,
                    icon = iconDrawable
                )
            }
            .toList()

        val totalScreenTimeMs = validApps.sumOf { it.usageTimeMs }
        val topApps = validApps.take(limit)

        return DailyUsageSummary(
            totalScreenTimeMs = totalScreenTimeMs,
            topApps = topApps
        )
    }

    internal fun processEventRecords(
        records: List<UsageEventRecord>,
        startOfDay: Long,
        now: Long,
        excludedPackages: Set<String>
    ): Map<String, Long> {
        val usageMap = HashMap<String, Long>()
        var currentForegroundPackage: String? = null
        var currentSessionStart = 0L
        var isScreenInteractive = true

        fun closeCurrentSession(endTime: Long) {
            val pkg = currentForegroundPackage ?: return
            val sessionStart = maxOf(currentSessionStart, startOfDay)
            val sessionEnd = minOf(endTime, now)
            if (sessionEnd > sessionStart && !excludedPackages.contains(pkg)) {
                usageMap[pkg] = usageMap.getOrDefault(pkg, 0L) + (sessionEnd - sessionStart)
            }
            currentForegroundPackage = null
            currentSessionStart = 0L
        }

        for (record in records) {
            val eventTime = record.timeStamp
            val pkg = record.packageName

            when (record.eventType) {
                // ACTIVITY_RESUMED (1) / MOVE_TO_FOREGROUND (1)
                1 -> {
                    if (currentForegroundPackage != null && currentForegroundPackage != pkg) {
                        closeCurrentSession(eventTime)
                    }
                    currentForegroundPackage = pkg
                    currentSessionStart = eventTime
                    isScreenInteractive = true
                }

                // ACTIVITY_PAUSED (2) / MOVE_TO_BACKGROUND (2)
                2 -> {
                    if (currentForegroundPackage == pkg) {
                        closeCurrentSession(eventTime)
                    }
                }

                // SCREEN_NON_INTERACTIVE (16), KEYGUARD_SHOWN (18), DEVICE_SHUTDOWN (26)
                16, 18, 26 -> {
                    closeCurrentSession(eventTime)
                    isScreenInteractive = false
                }

                // SCREEN_INTERACTIVE (15), KEYGUARD_HIDDEN (17)
                15, 17 -> {
                    isScreenInteractive = true
                }
            }
        }

        // App actively in foreground right now
        if (currentForegroundPackage != null && isScreenInteractive) {
            closeCurrentSession(now)
        }

        return usageMap
    }

    fun getTop5UsedAppsToday(context: Context): List<AppUsageInfo> = getTopUsedAppsToday(context, limit = 5)

    fun getTopUsedAppsToday(context: Context, limit: Int = 10): List<AppUsageInfo> {
        return getTodayUsageSummary(context, limit).topApps
    }

    fun getAppUsageTodayMs(context: Context, packageName: String): Long {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) return 0L
        val summary = getTodayUsageSummary(context, limit = 100)
        return summary.topApps.find { it.packageName == packageName }?.usageTimeMs ?: 0L
    }

    fun getInstalledUserApps(context: Context, selectedPackages: Set<String>): List<SelectableAppInfo> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val launcherPackages = packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        val excluded = setOf(
            context.packageName,
            "com.android.systemui",
            "android"
        ) + launcherPackages

        return resolveInfos
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { !excluded.contains(it) }
            .map { pkgName ->
                var label = pkgName
                var iconDrawable: Drawable? = null
                try {
                    val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                    label = packageManager.getApplicationLabel(appInfo).toString()
                    iconDrawable = packageManager.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                }
                SelectableAppInfo(
                    packageName = pkgName,
                    appName = label,
                    icon = iconDrawable,
                    isSelected = selectedPackages.contains(pkgName),
                    usageTimeMs = 0L
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    fun getCurrentlyForegroundPackage(context: Context): String? {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000L

        val events = usageStatsManager.queryEvents(startTime, endTime)
        var foregroundPackage: String? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foregroundPackage = event.packageName
            }
        }
        return foregroundPackage
    }

    fun buildGuiltTripMessage(topApps: List<AppUsageInfo>): String {
        if (topApps.isEmpty()) {
            return "A task is past due. Choose one small next step when you are ready."
        }
        val appListFormatted = topApps.joinToString(", ") { "${it.appName}: ${it.formattedTime}" }
        return "Today's app use: $appListFormatted"
    }

    fun buildNeutralScreenTimeSummary(
        topApps: List<AppUsageInfo>,
        @Suppress("UNUSED_PARAMETER") overdueCount: Int = 0
    ): String {
        if (topApps.isEmpty()) {
            return "Open apps normally throughout the day to see your top foreground durations here."
        }
        val topSummary = topApps.take(3).joinToString(", ") { "${it.appName} (${it.formattedTime})" }
        return "Most used today: $topSummary."
    }
}
