package com.example.dueordie

import com.example.dueordie.util.TimeFormatUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatUtilsTest {

    @Test
    fun testFormatCountdown_FutureTime() {
        val now = 1000000L
        val deadlineIn2Hours = now + (2 * 3600 * 1000L) + (15 * 60 * 1000L) + (30 * 1000L)
        val formatted = TimeFormatUtils.formatCountdown(deadlineIn2Hours, now)
        assertTrue("Formatted countdown should mention hours remaining", formatted.contains("02h 15m 30s remaining"))
    }

    @Test
    fun testFormatCountdown_OverdueTime() {
        val now = 10000000L
        val deadlineInPast = now - (1 * 3600 * 1000L) - (5 * 60 * 1000L) - (20 * 1000L)
        val formatted = TimeFormatUtils.formatCountdown(deadlineInPast, now)
        assertTrue("Formatted countdown should mention OVERDUE", formatted.contains("OVERDUE by 01h 05m 20s"))
    }

    @Test
    fun testFormatCountdown_DaysRemaining() {
        val now = 10000000L
        val deadlineIn3Days = now + (3 * 24 * 3600 * 1000L) + (4 * 3600 * 1000L)
        val formatted = TimeFormatUtils.formatCountdown(deadlineIn3Days, now)
        assertTrue("Formatted countdown should mention days remaining", formatted.contains("3d 4h remaining"))
    }
}
