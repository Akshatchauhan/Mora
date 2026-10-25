package com.example.dueordie

import com.example.dueordie.util.AppUsageInfo
import com.example.dueordie.util.UsageEventRecord
import com.example.dueordie.util.UsageStatsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsHelperTest {

    @Test
    fun testAppUsageInfo_FormattedTime_HoursAndMinutes() {
        val usageMs = (2 * 3600 * 1000L) + (15 * 60 * 1000L) // 2 hrs 15 mins
        val info = AppUsageInfo(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            usageTimeMs = usageMs
        )
        assertEquals("2 hrs 15 mins", info.formattedTime)
    }

    @Test
    fun testAppUsageInfo_FormattedTime_SingleHourAndMinute() {
        val usageMs = (1 * 3600 * 1000L) + (1 * 60 * 1000L) // 1 hr 1 min
        val info = AppUsageInfo(
            packageName = "com.instagram.android",
            appName = "Instagram",
            usageTimeMs = usageMs
        )
        assertEquals("1 hr 1 min", info.formattedTime)
    }

    @Test
    fun testAppUsageInfo_FormattedTime_MinutesOnly() {
        val usageMs = 45 * 60 * 1000L // 45 mins
        val info = AppUsageInfo(
            packageName = "com.android.chrome",
            appName = "Chrome",
            usageTimeMs = usageMs
        )
        assertEquals("45 mins", info.formattedTime)
    }

    @Test
    fun testBuildGuiltTripMessage_EmptyList() {
        val message = UsageStatsHelper.buildGuiltTripMessage(emptyList())
        assertEquals("A task is past due. Choose one small next step when you are ready.", message)
    }

    @Test
    fun testBuildGuiltTripMessage_WithTopApps() {
        val apps = listOf(
            AppUsageInfo("com.google.android.youtube", "YouTube", (2 * 3600 * 1000L) + (15 * 60 * 1000L)),
            AppUsageInfo("com.instagram.android", "Instagram", (1 * 3600 * 1000L) + (30 * 60 * 1000L))
        )
        val message = UsageStatsHelper.buildGuiltTripMessage(apps)
        val expected = "Today's app use: YouTube: 2 hrs 15 mins, Instagram: 1 hr 30 mins"
        assertEquals(expected, message)
        assertTrue(message.startsWith("Today's app use:"))
    }

    @Test
    fun testProcessEventRecords_StandardAppSession() {
        val startOfDay = 1_000_000L
        val now = 2_000_000L
        val records = listOf(
            UsageEventRecord(eventType = 1, packageName = "com.whatsapp", timeStamp = 1_100_000L), // resume
            UsageEventRecord(eventType = 2, packageName = "com.whatsapp", timeStamp = 1_400_000L)  // pause (+300s = 300,000ms)
        )

        val result = UsageStatsHelper.processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = emptySet()
        )

        assertEquals(300_000L, result["com.whatsapp"])
    }

    @Test
    fun testProcessEventRecords_AcrossMidnightClamping() {
        val startOfDay = 10_000_000L
        val now = 12_000_000L
        // App started 500,000ms before midnight and paused 400,000ms after midnight
        val records = listOf(
            UsageEventRecord(eventType = 1, packageName = "com.instagram.android", timeStamp = 9_500_000L),
            UsageEventRecord(eventType = 2, packageName = "com.instagram.android", timeStamp = 10_400_000L)
        )

        val result = UsageStatsHelper.processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = emptySet()
        )

        // Only the 400,000ms since midnight must be counted for today
        assertEquals(400_000L, result["com.instagram.android"])
    }

    @Test
    fun testProcessEventRecords_DirectAppSwitch() {
        val startOfDay = 1_000_000L
        val now = 2_000_000L
        val records = listOf(
            UsageEventRecord(eventType = 1, packageName = "com.google.android.youtube", timeStamp = 1_100_000L),
            // User switched directly to WhatsApp without prior pause event
            UsageEventRecord(eventType = 1, packageName = "com.whatsapp", timeStamp = 1_500_000L),
            UsageEventRecord(eventType = 2, packageName = "com.whatsapp", timeStamp = 1_800_000L)
        )

        val result = UsageStatsHelper.processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = emptySet()
        )

        assertEquals(400_000L, result["com.google.android.youtube"])
        assertEquals(300_000L, result["com.whatsapp"])
    }

    @Test
    fun testProcessEventRecords_ExcludesLauncherAndSystem() {
        val startOfDay = 1_000_000L
        val now = 2_000_000L
        val records = listOf(
            UsageEventRecord(eventType = 1, packageName = "com.android.launcher", timeStamp = 1_050_000L),
            UsageEventRecord(eventType = 1, packageName = "com.example.app", timeStamp = 1_200_000L),
            UsageEventRecord(eventType = 2, packageName = "com.example.app", timeStamp = 1_500_000L),
            UsageEventRecord(eventType = 1, packageName = "com.android.launcher", timeStamp = 1_510_000L)
        )

        val excluded = setOf("com.android.launcher", "com.android.systemui")
        val result = UsageStatsHelper.processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = excluded
        )

        assertEquals(300_000L, result["com.example.app"])
        assertEquals(null, result["com.android.launcher"])
    }

    @Test
    fun testProcessEventRecords_ScreenNonInteractiveEndsSession() {
        val startOfDay = 1_000_000L
        val now = 2_000_000L
        val records = listOf(
            UsageEventRecord(eventType = 1, packageName = "com.twitter.android", timeStamp = 1_100_000L),
            // Screen turned off at 1_350_000L
            UsageEventRecord(eventType = 16, packageName = "android", timeStamp = 1_350_000L)
        )

        val result = UsageStatsHelper.processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = emptySet()
        )

        assertEquals(250_000L, result["com.twitter.android"])
    }

    @Test
    fun testProcessEventRecords_CurrentlyRunningApp() {
        val startOfDay = 1_000_000L
        val now = 1_500_000L
        val records = listOf(
            UsageEventRecord(eventType = 1, packageName = "com.intellectexplorer.mora", timeStamp = 1_200_000L)
        )

        val result = UsageStatsHelper.processEventRecords(
            records = records,
            startOfDay = startOfDay,
            now = now,
            excludedPackages = emptySet()
        )

        // App running from 1_200_000L to now (1_500_000L)
        assertEquals(300_000L, result["com.intellectexplorer.mora"])
    }
}
