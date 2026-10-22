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

object UsageStatsHelper {

    @Suppress("DEPRECATION")
    fun getTop5UsedAppsToday(context: Context): List<AppUsageInfo> = getTopUsedAppsToday(context, limit = 5)

    @Suppress("DEPRECATION")
    fun getTopUsedAppsToday(context: Context, limit: Int = 10): List<AppUsageInfo> {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageMap = HashMap<String, Long>()

        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (!statsList.isNullOrEmpty()) {
            for (stat in statsList) {
                if (stat.totalTimeInForeground > 0) {
                    val current = usageMap.getOrDefault(stat.packageName, 0L)
                    usageMap[stat.packageName] = maxOf(current, stat.totalTimeInForeground)
                }
            }
        }

        if (usageMap.isEmpty()) {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var lastEventTime = 0L
            var lastPackage = ""
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastEventTime = event.timeStamp
                    lastPackage = event.packageName
                } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && event.packageName == lastPackage && lastEventTime > 0) {
                    val duration = event.timeStamp - lastEventTime
                    if (duration > 0) {
                        usageMap[lastPackage] = usageMap.getOrDefault(lastPackage, 0L) + duration
                    }
                    lastEventTime = 0L
                }
            }
        }

        val packageManager = context.packageManager
        val selfPackage = context.packageName

        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val launcherPackages = packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        val excludedPackages = setOf(
            selfPackage,
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "android"
        ) + launcherPackages

        return usageMap.entries
            .asSequence()
            .filter { entry ->
                val pkg = entry.key
                entry.value > 0 &&
                        !excludedPackages.contains(pkg) &&
                        packageManager.getLaunchIntentForPackage(pkg) != null
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
            .take(limit)
            .toList()
    }

    fun getAppUsageTodayMs(context: Context, packageName: String): Long {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) return 0L
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        val statsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return statsList?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
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
