package com.example.dueordie

import com.example.dueordie.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun testFormatDuration_DaysAndHours() {
        val durationMs = (2 * 24 * 3600 + 5 * 3600) * 1000L
        val formatted = NotificationHelper.formatDuration(durationMs)
        assertEquals("2d 5h", formatted)
    }

    @Test
    fun testFormatDuration_HoursAndMinutes() {
        val durationMs = (3 * 3600 + 15 * 60) * 1000L
        val formatted = NotificationHelper.formatDuration(durationMs)
        assertEquals("3h 15m", formatted)
    }

    @Test
    fun testFormatDuration_MinutesOnly() {
        val durationMs = 45 * 60 * 1000L
        val formatted = NotificationHelper.formatDuration(durationMs)
        assertEquals("45m", formatted)
    }

    @Test
    fun testFormatDuration_LessThanOneMinute() {
        val durationMs = 30 * 1000L
        val formatted = NotificationHelper.formatDuration(durationMs)
        assertEquals("< 1m", formatted)
    }

    @Test
    fun testFormatDeadlineCountdown_FutureDeadline() {
        val now = 1000000000000L
        val deadline = now + (2 * 3600 * 1000L) // +2 hours
        val countdown = NotificationHelper.formatDeadlineCountdown(deadline, now)
        assertEquals("Due in 2h 0m", countdown)
    }

    @Test
    fun testFormatDeadlineCountdown_PastDeadline_Overdue() {
        val now = 1000000000000L
        val deadline = now - (15 * 60 * 1000L) // -15 minutes
        val countdown = NotificationHelper.formatDeadlineCountdown(deadline, now)
        assertEquals("Overdue by 15m", countdown)
    }
}
