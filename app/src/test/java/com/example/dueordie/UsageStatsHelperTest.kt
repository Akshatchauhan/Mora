package com.example.dueordie

import com.example.dueordie.util.AppUsageInfo
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
}
