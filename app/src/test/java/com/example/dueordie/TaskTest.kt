package com.example.dueordie

import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.UrgencyLevel
import com.example.dueordie.worker.WorkManagerScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskTest {

    @Test
    fun testUrgencyLevelCalculation_LowUrgency() {
        val now = System.currentTimeMillis()
        val deadlineIn30Hours = now + (30 * 60 * 60 * 1000L)
        val task = Task(
            title = "Test Task",
            deadlineTimestamp = deadlineIn30Hours,
            status = TaskStatus.PENDING
        )

        val urgency = task.calculateUrgencyLevel(now)
        assertEquals(UrgencyLevel.LOW, urgency)
        assertFalse(task.isOverdue)
    }

    @Test
    fun testUrgencyLevelCalculation_MediumUrgency() {
        val now = System.currentTimeMillis()
        val deadlineIn18Hours = now + (18 * 60 * 60 * 1000L)
        val task = Task(
            title = "Medium Urgency Task",
            deadlineTimestamp = deadlineIn18Hours,
            status = TaskStatus.PENDING
        )

        val urgency = task.calculateUrgencyLevel(now)
        assertEquals(UrgencyLevel.MEDIUM, urgency)
    }

    @Test
    fun testUrgencyLevelCalculation_HighUrgency() {
        val now = System.currentTimeMillis()
        val deadlineIn6Hours = now + (6 * 60 * 60 * 1000L)
        val task = Task(
            title = "High Urgency Task",
            deadlineTimestamp = deadlineIn6Hours,
            status = TaskStatus.PENDING
        )

        val urgency = task.calculateUrgencyLevel(now)
        assertEquals(UrgencyLevel.HIGH, urgency)
    }

    @Test
    fun testUrgencyLevelCalculation_CriticalUrgency() {
        val now = System.currentTimeMillis()
        val deadlineIn1Hour = now + (1 * 60 * 60 * 1000L)
        val task = Task(
            title = "Critical Urgency Task",
            deadlineTimestamp = deadlineIn1Hour,
            status = TaskStatus.PENDING
        )

        val urgency = task.calculateUrgencyLevel(now)
        assertEquals(UrgencyLevel.CRITICAL, urgency)
    }

    @Test
    fun testUrgencyLevelCalculation_Overdue() {
        val now = System.currentTimeMillis()
        val deadlineInPast = now - (1 * 60 * 60 * 1000L)
        val task = Task(
            title = "Overdue Task",
            deadlineTimestamp = deadlineInPast,
            status = TaskStatus.PENDING
        )

        val urgency = task.calculateUrgencyLevel(now)
        assertEquals(UrgencyLevel.OVERDUE, urgency)
    }

    @Test
    fun testUrgencyLevelCalculation_CompletedTaskIsLow() {
        val now = System.currentTimeMillis()
        val deadlineInPast = now - (10 * 60 * 60 * 1000L)
        val task = Task(
            title = "Completed Task",
            deadlineTimestamp = deadlineInPast,
            status = TaskStatus.COMPLETED
        )

        val urgency = task.calculateUrgencyLevel(now)
        assertEquals(UrgencyLevel.LOW, urgency)
    }

    @Test
    fun testCalculateInitialDelayForHour_FutureToday() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val delay = WorkManagerScheduler.calculateInitialDelayForHour(9, calendar)
        // Expected delay is 1 hour = 3600000 ms
        assertEquals(3600000L, delay)
    }

    @Test
    fun testCalculateInitialDelayForHour_PastToday_SchedulesTomorrow() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val delay = WorkManagerScheduler.calculateInitialDelayForHour(9, calendar)
        // Expected delay is 23 hours = 23 * 3600000 ms
        assertEquals(23 * 3600000L, delay)
    }
}
