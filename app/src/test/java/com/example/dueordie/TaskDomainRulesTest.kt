package com.example.dueordie

import com.example.dueordie.data.model.CompletedTaskRecord
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class TaskDomainRulesTest {

    @Test
    fun determineStatusForDeadline_overdueTaskRescheduledToFuture_becomesPending() {
        val now = 1_700_000_000_000L
        val futureDeadline = now + 3_600_000L

        val newStatus = TaskDomainRules.determineStatusForDeadline(
            currentStatus = TaskStatus.OVERDUE,
            deadlineTimestamp = futureDeadline,
            nowMs = now
        )

        assertEquals(TaskStatus.PENDING, newStatus)
    }

    @Test
    fun determineStatusForDeadline_pendingTaskMovedToPast_becomesOverdue() {
        val now = 1_700_000_000_000L
        val pastDeadline = now - 60_000L

        val newStatus = TaskDomainRules.determineStatusForDeadline(
            currentStatus = TaskStatus.PENDING,
            deadlineTimestamp = pastDeadline,
            nowMs = now
        )

        assertEquals(TaskStatus.OVERDUE, newStatus)
    }

    @Test
    fun determineStatusForDeadline_completedTask_staysCompleted() {
        val now = 1_700_000_000_000L
        val futureDeadline = now + 3_600_000L

        val newStatus = TaskDomainRules.determineStatusForDeadline(
            currentStatus = TaskStatus.COMPLETED,
            deadlineTimestamp = futureDeadline,
            nowMs = now
        )

        assertEquals(TaskStatus.COMPLETED, newStatus)
    }

    @Test
    fun resolveEditedTask_completedTaskFreezesDeadlineAndCompletionOutcome() {
        val now = 1_700_000_000_000L
        val originalDeadline = now - 7_200_000L
        val completedLateAt = now - 3_600_000L // completed 1h after deadline (late)

        val completedTask = Task(
            id = 42,
            title = "Submit taxes",
            description = "Old note",
            deadlineTimestamp = originalDeadline,
            status = TaskStatus.COMPLETED,
            completedAtTimestamp = completedLateAt,
            isHostageModeEnabled = true
        )
        assertTrue(completedTask.isCompletedLate)

        // Attempt to edit title/description AND push deadline into the future
        val edited = TaskDomainRules.resolveEditedTask(
            existingTask = completedTask,
            newTitle = "Submit taxes (updated note)",
            newDescription = "Filed receipt #123",
            requestedDeadlineTimestamp = now + 86_400_000L,
            requirePhotoProof = false,
            effectivePhotoPath = null,
            isHostageModeEnabled = false,
            nowMs = now
        )

        assertEquals("Submit taxes (updated note)", edited.title)
        assertEquals("Filed receipt #123", edited.description)
        assertEquals(originalDeadline, edited.deadlineTimestamp)
        assertEquals(completedLateAt, edited.completedAtTimestamp)
        assertTrue(edited.isCompletedLate)
        assertFalse(edited.isCompletedOnTime)
    }

    @Test
    fun calculateOnTimeRate_noCompletedOrOverdueTasks_returnsNull() {
        val now = 1_700_000_000_000L
        val tasks = listOf(
            Task(
                id = 1,
                title = "Future pending task",
                deadlineTimestamp = now + 3_600_000L,
                status = TaskStatus.PENDING
            )
        )

        assertNull(TaskDomainRules.calculateOnTimeRate(tasks, now))
    }

    @Test
    fun calculateOnTimeRate_onlyOverdueNoCompletedTasks_returnsNull() {
        val now = 1_700_000_000_000L
        val tasks = listOf(
            Task(
                id = 1,
                title = "Past due task",
                deadlineTimestamp = now - 3_600_000L,
                status = TaskStatus.OVERDUE
            )
        )

        assertNull(TaskDomainRules.calculateOnTimeRate(tasks, now))
    }

    @Test
    fun calculateOnTimeRate_lateCompletionCountsAgainstRate() {
        val now = 1_700_000_000_000L
        val onTimeTask = Task(
            id = 1,
            title = "On time",
            deadlineTimestamp = now - 3_600_000L,
            status = TaskStatus.COMPLETED,
            completedAtTimestamp = now - 7_200_000L // completed 1h before deadline
        )
        val lateTask = Task(
            id = 2,
            title = "Completed late",
            deadlineTimestamp = now - 7_200_000L,
            status = TaskStatus.COMPLETED,
            completedAtTimestamp = now - 3_600_000L // completed 1h after deadline
        )

        assertTrue(onTimeTask.isCompletedOnTime)
        assertFalse(onTimeTask.isCompletedLate)
        assertFalse(lateTask.isCompletedOnTime)
        assertTrue(lateTask.isCompletedLate)

        val rate = TaskDomainRules.calculateOnTimeRate(listOf(onTimeTask, lateTask), now)
        assertEquals(50, rate)
    }

    @Test
    fun calculateOnTimeRate_activeOverdueTaskLowersRate() {
        val now = 1_700_000_000_000L
        val onTimeTask = Task(
            id = 1,
            title = "On time",
            deadlineTimestamp = now - 3_600_000L,
            status = TaskStatus.COMPLETED,
            completedAtTimestamp = now - 4_000_000L
        )
        val overdueTask = Task(
            id = 2,
            title = "Currently overdue",
            deadlineTimestamp = now - 1_800_000L,
            status = TaskStatus.OVERDUE
        )

        val rate = TaskDomainRules.calculateOnTimeRate(listOf(onTimeTask, overdueTask), now)
        assertEquals(50, rate)
    }

    @Test
    fun calculateOnTimeRate_andStreak_preservedWhenCompletedTasksAreDeleted() {
        val tz = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day0 = nowCal.timeInMillis
        val dayMinus1 = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis

        // Suppose task 1 (on-time yesterday) and task 2 (late today) were both deleted from Room,
        // while task 3 (on-time today) remains in Room and is also in archivedRecords.
        val archived = listOf(
            CompletedTaskRecord(
                taskId = 1,
                deadlineTimestamp = dayMinus1 + 60_000L,
                completedAtTimestamp = dayMinus1
            ),
            CompletedTaskRecord(
                taskId = 2,
                deadlineTimestamp = day0 - 120_000L,
                completedAtTimestamp = day0 - 60_000L // late
            ),
            CompletedTaskRecord(
                taskId = 3,
                deadlineTimestamp = day0 + 60_000L,
                completedAtTimestamp = day0
            )
        )
        val remainingActiveTasks = listOf(
            Task(
                id = 3,
                title = "Still in list",
                deadlineTimestamp = day0 + 60_000L,
                status = TaskStatus.COMPLETED,
                completedAtTimestamp = day0
            )
        )

        // 2 on-time out of 3 total completed = 66% (not 100% despite deleting the late task)
        val rate = TaskDomainRules.calculateOnTimeRate(
            tasks = remainingActiveTasks,
            nowMs = day0,
            archivedRecords = archived
        )
        assertEquals(66, rate)

        // 2 consecutive days (yesterday from archived record + today)
        val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
            tasks = remainingActiveTasks,
            nowMs = day0,
            timeZone = tz,
            archivedRecords = archived
        )
        assertEquals(2, streak)
    }

    @Test
    fun calculateConsecutiveOnTimeStreak_singleOldTaskTenDaysAgo_returnsZero() {
        val tz = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 10, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tenDaysAgo = (nowCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -10)
        }.timeInMillis

        val oldTask = Task(
            id = 1,
            title = "Old task",
            deadlineTimestamp = tenDaysAgo + 3_600_000L,
            status = TaskStatus.COMPLETED,
            completedAtTimestamp = tenDaysAgo
        )

        val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
            tasks = listOf(oldTask),
            nowMs = nowCal.timeInMillis,
            timeZone = tz
        )
        assertEquals(0, streak)
    }

    @Test
    fun calculateConsecutiveOnTimeStreak_multipleSameDayCompletions_countsAsOneDay() {
        val tz = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 10, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morning = (nowCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis
        val afternoon = (nowCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 15) }.timeInMillis

        val tasks = listOf(
            Task(id = 1, title = "T1", deadlineTimestamp = morning + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = morning),
            Task(id = 2, title = "T2", deadlineTimestamp = afternoon + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = afternoon)
        )

        val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(tasks, nowCal.timeInMillis, tz)
        assertEquals(1, streak)
    }

    @Test
    fun calculateConsecutiveOnTimeStreak_threeConsecutiveDays_returnsThree() {
        val tz = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day0 = nowCal.timeInMillis
        val dayMinus1 = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val dayMinus2 = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis

        val tasks = listOf(
            Task(id = 1, title = "D0", deadlineTimestamp = day0 + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = day0),
            Task(id = 2, title = "D1", deadlineTimestamp = dayMinus1 + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = dayMinus1),
            Task(id = 3, title = "D2", deadlineTimestamp = dayMinus2 + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = dayMinus2)
        )

        val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(tasks, day0, tz)
        assertEquals(3, streak)
    }

    @Test
    fun calculateConsecutiveOnTimeStreak_missedYesterday_stopsAtToday() {
        val tz = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day0 = nowCal.timeInMillis
        val dayMinus2 = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis

        val tasks = listOf(
            Task(id = 1, title = "D0", deadlineTimestamp = day0 + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = day0),
            Task(id = 2, title = "D2", deadlineTimestamp = dayMinus2 + 1000, status = TaskStatus.COMPLETED, completedAtTimestamp = dayMinus2)
        )

        val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(tasks, day0, tz)
        assertEquals(1, streak)
    }

    @Test
    fun calculateConsecutiveOnTimeStreak_lateCompletionDoesNotExtendStreak() {
        val tz = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day0 = nowCal.timeInMillis
        val lateTaskToday = Task(
            id = 1,
            title = "Late today",
            deadlineTimestamp = day0 - 60_000L,
            status = TaskStatus.COMPLETED,
            completedAtTimestamp = day0 // completed 1m after deadline
        )

        val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(listOf(lateTaskToday), day0, tz)
        assertEquals(0, streak)
    }

    @Test
    fun evaluateFocusLock_intervenesOnlyWhenEnabledPermittedBlockedAndOverdue() {
        val now = 1_700_000_000_000L
        val overdueTask = Task(
            id = 10,
            title = "Finish report",
            deadlineTimestamp = now - (25 * 60 * 1000L),
            status = TaskStatus.OVERDUE,
            isHostageModeEnabled = true
        )
        val blocked = setOf("com.google.android.youtube")

        val activeDecision = TaskDomainRules.evaluateFocusLock(
            isGlobalFocusLockEnabled = true,
            hasUsagePermission = true,
            foregroundPackage = "com.google.android.youtube",
            selfPackage = "com.example.dueordie",
            blockedPackages = blocked,
            tasks = listOf(overdueTask),
            nowMs = now
        )
        assertTrue(activeDecision.shouldIntervene)
        assertNotNull(activeDecision.blockingTask)
        assertEquals("Finish report", activeDecision.blockingTask?.title)
        assertEquals(25L, activeDecision.overdueMinutes)

        val disabledDecision = TaskDomainRules.evaluateFocusLock(
            isGlobalFocusLockEnabled = false,
            hasUsagePermission = true,
            foregroundPackage = "com.google.android.youtube",
            selfPackage = "com.example.dueordie",
            blockedPackages = blocked,
            tasks = listOf(overdueTask),
            nowMs = now
        )
        assertFalse(disabledDecision.shouldIntervene)
    }

    @Test
    fun focusLockCooldownRunsForTenMinutesAndStopsAtExpiry() {
        val now = 1_700_000_000_000L
        val blocked = setOf("com.instagram.android")
        val active = TaskDomainRules.evaluateFocusLock(
            isGlobalFocusLockEnabled = true,
            hasUsagePermission = true,
            foregroundPackage = "com.instagram.android",
            selfPackage = "com.intellectexplorer.mora",
            blockedPackages = blocked,
            tasks = emptyList(),
            nowMs = now,
            cooldownUntilMs = now + 10 * 60_000L,
            cooldownTaskTitle = "Write report"
        )
        assertTrue(active.shouldIntervene)
        assertTrue(active.isCooldownActive)
        assertEquals(10, active.cooldownRemainingMinutes)
        assertEquals("Write report", active.blockingTask?.title)
        assertTrue(TaskDomainRules.shouldRunFocusLockMonitor(true, true, emptyList(), now, now + 10 * 60_000L))

        val expired = TaskDomainRules.evaluateFocusLock(
            true, true, "com.instagram.android", "com.intellectexplorer.mora", blocked,
            emptyList(), now + 10 * 60_000L, now + 10 * 60_000L
        )
        assertFalse(expired.shouldIntervene)
        assertFalse(TaskDomainRules.shouldRunFocusLockMonitor(true, true, emptyList(), now + 10 * 60_000L, now + 10 * 60_000L))

        // Focus Lock remains explicitly user-disableable even while a cooldown preference exists.
        val optedOut = TaskDomainRules.evaluateFocusLock(
            false, true, "com.instagram.android", "com.intellectexplorer.mora", blocked,
            emptyList(), now, now + 10 * 60_000L
        )
        assertFalse(optedOut.shouldIntervene)
    }

    @Test
    fun evaluateFocusLock_mixedTasks_taskWithFocusLockDisabledNeverTriggersOrGetsPicked() {
        val now = 1_700_000_000_000L
        val olderOverdueWithoutFocusLock = Task(
            id = 1,
            title = "Optional reading",
            deadlineTimestamp = now - (120 * 60 * 1000L), // 2h overdue, but Focus Lock OFF
            status = TaskStatus.OVERDUE,
            isHostageModeEnabled = false
        )
        val newerOverdueWithFocusLock = Task(
            id = 2,
            title = "Pay rent",
            deadlineTimestamp = now - (15 * 60 * 1000L), // 15m overdue, Focus Lock ON
            status = TaskStatus.OVERDUE,
            isHostageModeEnabled = true
        )
        val blocked = setOf("com.instagram.android")

        // 1. Only the task with Focus Lock OFF is overdue -> must NOT intervene
        val nonFocusLockOnlyDecision = TaskDomainRules.evaluateFocusLock(
            isGlobalFocusLockEnabled = true,
            hasUsagePermission = true,
            foregroundPackage = "com.instagram.android",
            selfPackage = "com.example.dueordie",
            blockedPackages = blocked,
            tasks = listOf(olderOverdueWithoutFocusLock),
            nowMs = now
        )
        assertFalse(nonFocusLockOnlyDecision.shouldIntervene)
        assertNull(nonFocusLockOnlyDecision.blockingTask)
        assertFalse(
            TaskDomainRules.shouldRunFocusLockMonitor(
                isGlobalFocusLockEnabled = true,
                hasPermission = true,
                tasks = listOf(olderOverdueWithoutFocusLock),
                nowMs = now
            )
        )

        // 2. Both tasks are overdue -> must pick "Pay rent" (id=2), NOT the older task with Focus Lock OFF
        val mixedDecision = TaskDomainRules.evaluateFocusLock(
            isGlobalFocusLockEnabled = true,
            hasUsagePermission = true,
            foregroundPackage = "com.instagram.android",
            selfPackage = "com.example.dueordie",
            blockedPackages = blocked,
            tasks = listOf(olderOverdueWithoutFocusLock, newerOverdueWithFocusLock),
            nowMs = now
        )
        assertTrue(mixedDecision.shouldIntervene)
        assertEquals(2L, mixedDecision.blockingTask?.id)
        assertEquals("Pay rent", mixedDecision.blockingTask?.title)
        assertEquals(15L, mixedDecision.overdueMinutes)
        assertTrue(
            TaskDomainRules.shouldRunFocusLockMonitor(
                isGlobalFocusLockEnabled = true,
                hasPermission = true,
                tasks = listOf(olderOverdueWithoutFocusLock, newerOverdueWithFocusLock),
                nowMs = now
            )
        )
    }

    @Test
    fun deletePhotoFileIfPresent_deletesExistingTempFile() {
        val tempFile = File.createTempFile("test_task_photo_", ".jpg")
        assertTrue(tempFile.exists())

        val deleted = TaskDomainRules.deletePhotoFileIfPresent(tempFile.absolutePath)
        assertTrue(deleted)
        assertFalse(tempFile.exists())
        assertFalse(TaskDomainRules.deletePhotoFileIfPresent(null))
    }
}
