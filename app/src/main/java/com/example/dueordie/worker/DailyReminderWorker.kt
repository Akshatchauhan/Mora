package com.example.dueordie.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dueordie.data.local.AppDatabase
import com.example.dueordie.data.model.UrgencyLevel
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.CompletionHistoryStore
import com.example.dueordie.util.MoraNudgeEngine
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import java.util.Calendar

class DailyReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = OnboardingPreferences(context)
            if (!prefs.areRemindersEnabled || !PermissionHelper.isNotificationPermissionGranted(context)) {
                return Result.success()
            }

            val database = AppDatabase.getDatabase(context)
            val dao = database.taskDao()
            val allTasks = dao.getAllTasksOneShot()
            val pendingTasks = allTasks.filter { it.status != TaskStatus.COMPLETED }

            if (pendingTasks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val criticalCount = pendingTasks.count { it.calculateUrgencyLevel(now) == UrgencyLevel.CRITICAL }
                val overdueCount = pendingTasks.count { it.calculateUrgencyLevel(now) == UrgencyLevel.OVERDUE }

                val slotLabel = inputData.getString("SLOT_LABEL") ?: getSlotLabelForCurrentTime()
                val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
                    allTasks, now, archivedRecords = CompletionHistoryStore(context).getRecords()
                )
                val overdueTask = pendingTasks.filter { it.isOverdueAt(now) }.minByOrNull { it.deadlineTimestamp }
                val nextTask = pendingTasks.filterNot { it.isOverdueAt(now) }.minByOrNull { it.deadlineTimestamp }

                val title = "$slotLabel task check-in"
                val message = when {
                    overdueTask != null -> MoraNudgeEngine.buildOverdueNotificationCopy(overdueTask.title, overdueCount, streak).second
                    nextTask != null -> MoraNudgeEngine.buildReminderNotificationCopy(nextTask, ((nextTask.deadlineTimestamp - now) / 60_000L).coerceAtLeast(1), streak).second
                    criticalCount > 0 -> "$criticalCount task${if (criticalCount == 1) " is" else "s are"} due within 3 hours (${pendingTasks.size} pending total)."
                    else -> "You have ${pendingTasks.size} pending task${if (pendingTasks.size == 1) "" else "s"}."
                }

                NotificationHelper.showReminderNotification(
                    context = context,
                    title = title,
                    message = message,
                    notificationId = (2000 + slotLabel.hashCode() % 1000)
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun getSlotLabelForCurrentTime(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
    }
}
