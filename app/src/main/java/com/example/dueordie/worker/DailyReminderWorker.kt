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
                // Prevent duplicate notifications firing at the same time
                if (now - prefs.lastDailyCheckInTimestamp < 3 * 3600 * 1000L) {
                    return Result.success()
                }
                prefs.lastDailyCheckInTimestamp = now

                val criticalCount = pendingTasks.count { it.calculateUrgencyLevel(now) == UrgencyLevel.CRITICAL }
                val overdueCount = pendingTasks.count { it.calculateUrgencyLevel(now) == UrgencyLevel.OVERDUE }

                val slotLabel = getSlotLabelForCurrentTime()
                val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
                    allTasks, now, archivedRecords = CompletionHistoryStore(context).getRecords()
                )
                val overdueTask = pendingTasks.filter { it.isOverdueAt(now) }.minByOrNull { it.deadlineTimestamp }
                val nextTask = pendingTasks.filterNot { it.isOverdueAt(now) }.minByOrNull { it.deadlineTimestamp }

                val title = "$slotLabel Check-in"
                val message = when {
                    overdueTask != null && overdueTask.isHostageModeEnabled && prefs.isGlobalHostageModeEnabled ->
                        "Focus Lock active: \"${overdueTask.title}\" is past due."
                    overdueTask != null ->
                        "\"${overdueTask.title}\" is past its deadline. Take a moment to finish it."
                    nextTask != null -> {
                        val diffMin = ((nextTask.deadlineTimestamp - now) / 60_000L).coerceAtLeast(1)
                        "\"${nextTask.title}\" is due in ${diffMin}m."
                    }
                    criticalCount > 0 ->
                        "$criticalCount task${if (criticalCount == 1) " is" else "s are"} due within 3 hours."
                    else ->
                        "You have ${pendingTasks.size} task${if (pendingTasks.size == 1) "" else "s"} remaining today."
                }

                NotificationHelper.showReminderNotification(
                    context = context,
                    title = title,
                    message = message,
                    notificationId = NotificationHelper.NOTIFICATION_ID_DAILY_CHECKIN
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
