package com.example.dueordie

import com.example.dueordie.data.model.InteractionModePolicy
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.TaskPriority
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.util.AppInteractionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionModePolicyTest {

    @Test
    fun casualMode_allowsEditingSavedTasks() {
        assertTrue(InteractionModePolicy.canEditTask(AppInteractionMode.CASUAL, isTaskSaved = true))
    }

    @Test
    fun strictMode_locksEditingSavedTasks() {
        assertFalse(InteractionModePolicy.canEditTask(AppInteractionMode.STRICT, isTaskSaved = true))
    }

    @Test
    fun strictMode_allowsNewTaskCreation() {
        assertTrue(InteractionModePolicy.canEditTask(AppInteractionMode.STRICT, isTaskSaved = false))
    }

    @Test
    fun casualMode_allowsRescheduling() {
        assertTrue(InteractionModePolicy.canReschedule(AppInteractionMode.CASUAL))
    }

    @Test
    fun strictMode_disallowsRescheduling() {
        assertFalse(InteractionModePolicy.canReschedule(AppInteractionMode.STRICT))
    }

    @Test
    fun casualMode_doesNotRequireHoldToComplete() {
        assertFalse(InteractionModePolicy.isHoldToCompleteRequired(AppInteractionMode.CASUAL))
        assertEquals(0L, InteractionModePolicy.getRequiredHoldDurationMs(AppInteractionMode.CASUAL))
    }

    @Test
    fun strictMode_requiresHoldToCompleteWith1200ms() {
        assertTrue(InteractionModePolicy.isHoldToCompleteRequired(AppInteractionMode.STRICT))
        assertEquals(1200L, InteractionModePolicy.getRequiredHoldDurationMs(AppInteractionMode.STRICT))
    }

    @Test
    fun taskDomainRules_preservesDeadlineAndPriority_whenCanEditDeadlineIsFalse() {
        val now = 1_700_000_000_000L
        val originalDeadline = now + 3_600_000L

        val task = Task(
            id = 10,
            title = "Original Title",
            description = "Original Description",
            deadlineTimestamp = originalDeadline,
            priority = TaskPriority.HIGH,
            status = TaskStatus.PENDING
        )

        val updated = TaskDomainRules.resolveEditedTask(
            existingTask = task,
            newTitle = "Updated Title",
            newDescription = "Updated Description",
            requestedDeadlineTimestamp = now + 7_200_000L,
            priority = TaskPriority.LOW,
            nowMs = now,
            canEditDeadline = false
        )

        assertEquals("Updated Title", updated.title)
        assertEquals("Updated Description", updated.description)
        assertEquals(originalDeadline, updated.deadlineTimestamp)
        assertEquals(TaskPriority.HIGH, updated.priority)
    }
}
