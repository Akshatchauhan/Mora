package com.example.dueordie

import com.example.dueordie.data.model.Task
import com.example.dueordie.util.MoraNudgeEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class MoraNudgeEngineTest {
    @Test fun focusCooldownCopyIsContextual() {
        val copy = MoraNudgeEngine.buildFocusLockNudge("Instagram", "Pay rent", 5, 1, true, 7)
        assertTrue(copy.headline.contains("7"))
        assertTrue(copy.subtext.contains("Pay rent"))
    }

    @Test fun pauseNudgeReferencesTaskAndContext() {
        val now = 1_700_000_000_000L
        val task = Task(title = "Send invoice", deadlineTimestamp = now + 40 * 60_000L)
        val copy = MoraNudgeEngine.buildPauseNudge("YouTube", listOf(task), 3, "", now)
        assertTrue(copy.headline.contains("Send invoice"))
        assertTrue(copy.subtext.contains("YouTube"))
        assertTrue(copy.badgeText.orEmpty().contains("Due soon"))
    }

    @Test fun overduePauseNudgeIncludesLateTaskContext() {
        val now = 1_700_000_000_000L
        val task = Task(title = "Submit form", deadlineTimestamp = now - 9 * 60_000L)
        val copy = MoraNudgeEngine.buildPauseNudge("Instagram", listOf(task), 1, "", now)
        assertTrue(copy.headline.contains("Submit form"))
        assertTrue(copy.headline.contains("9m"))
        assertTrue(copy.subtext.contains("Instagram"))
    }

    @Test fun reminderAndOverdueCopyIncludeTaskAndStreak() {
        val task = Task(title = "Prepare slides", deadlineTimestamp = 1_700_000_000_000L)
        val reminder = MoraNudgeEngine.buildReminderNotificationCopy(task, 15, 4)
        val overdue = MoraNudgeEngine.buildOverdueNotificationCopy(task.title, 2, 4)
        assertTrue(reminder.first.contains("Prepare slides"))
        assertTrue(reminder.second.contains("4-day"))
        assertTrue(overdue.first.contains("Prepare slides"))
        assertTrue(overdue.second.contains("4-day"))
    }
}
