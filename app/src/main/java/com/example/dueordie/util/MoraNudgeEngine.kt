package com.example.dueordie.util

import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus

/** Calm, factual copy shared by pause screens and notifications. */
data class MoraNudgeCopy(
    val headline: String,
    val subtext: String,
    val badgeText: String? = null
)

object MoraNudgeEngine {
    fun buildPauseNudge(
        appLabel: String,
        activeTasks: List<Task>,
        attemptCountToday: Int,
        fallbackPrompt: String,
        nowMs: Long = System.currentTimeMillis()
    ): MoraNudgeCopy {
        val nextTask = activeTasks
            .filter { it.status != TaskStatus.COMPLETED }
            .minByOrNull { it.deadlineTimestamp }
        val prompt = if (fallbackPrompt.isBlank() || fallbackPrompt == OnboardingPreferences.DEFAULT_PAUSE_PROMPT) {
            MindfulPrompts.getPromptForPackage(appLabel, attemptCountToday)
        } else fallbackPrompt

        if (nextTask == null) return MoraNudgeCopy("Pause before $appLabel", prompt)

        val minutesUntilDue = (nextTask.deadlineTimestamp - nowMs) / 60_000L
        return when {
            minutesUntilDue < 0 -> MoraNudgeCopy(
                headline = "${nextTask.title} is past due (${formatDuration(-minutesUntilDue)})",
                subtext = "Take a breath before opening $appLabel. Would you like to work on it now?",
                badgeText = "Past due"
            )
            minutesUntilDue <= 120 -> MoraNudgeCopy(
                headline = "${nextTask.title} is due in ${formatDuration(minutesUntilDue.coerceAtLeast(1L))}",
                subtext = "Would you like to make progress on it before opening $appLabel?",
                badgeText = "Due soon"
            )
            else -> MoraNudgeCopy(
                headline = "Pause before $appLabel",
                subtext = "${nextTask.title} is on today's plan. Pause and breathe before deciding.",
                badgeText = "Mindful pause"
            )
        }
    }

    fun buildFocusLockNudge(
        appLabel: String,
        taskTitle: String,
        overdueMinutes: Long,
        attemptCountToday: Int,
        isCooldownActive: Boolean = false,
        cooldownRemainingMinutes: Int = 0
    ): MoraNudgeCopy {
        if (isCooldownActive) {
            return MoraNudgeCopy(
                headline = "Focus pause ($cooldownRemainingMinutes min)",
                subtext = "You finished $taskTitle. $appLabel will be available in $cooldownRemainingMinutes min.",
                badgeText = "$cooldownRemainingMinutes min"
            )
        }
        val duration = formatDuration(overdueMinutes.coerceAtLeast(1L))
        val choices = listOf(
            "Complete $taskTitle to unlock $appLabel.",
            "$taskTitle is past due. Finish it to unlock $appLabel.",
            "Take a moment to finish $taskTitle before opening $appLabel."
        )
        val choice = choices[(attemptCountToday - 1).coerceAtLeast(0) % choices.size]
        return MoraNudgeCopy(
            headline = "$appLabel is on Focus Lock",
            subtext = choice,
            badgeText = "$duration past due"
        )
    }

    fun buildReminderNotificationCopy(
        task: Task,
        minutesLeft: Long,
        streakDays: Int
    ): Pair<String, String> {
        val title = "Due soon: ${task.title}"
        val streakSuffix = if (streakDays > 0) " Keep your $streakDays-day streak going!" else ""
        val message = if (minutesLeft <= 15) {
            "Due in $minutesLeft min. Take a few minutes to finish it.$streakSuffix"
        } else {
            "Due in ${formatDuration(minutesLeft)}. Take a few minutes to finish it.$streakSuffix"
        }
        return title to message
    }

    fun buildOverdueNotificationCopy(
        taskTitle: String,
        overdueCount: Int,
        streakDays: Int
    ): Pair<String, String> {
        val title = if (overdueCount == 1) {
            "Focus Lock active: $taskTitle is past due"
        } else {
            "Focus Lock active: $taskTitle and ${overdueCount - 1} more past-due tasks"
        }
        val streakSuffix = if (streakDays > 0) " Protect your $streakDays-day streak." else ""
        return title to "Complete the task to unlock your selected apps.$streakSuffix"
    }

    private fun formatDuration(minutes: Long): String = if (minutes < 60L) {
        "${minutes}m"
    } else {
        val hours = minutes / 60L
        val remainder = minutes % 60L
        if (remainder == 0L) "${hours}h" else "${hours}h ${remainder}m"
    }
}
