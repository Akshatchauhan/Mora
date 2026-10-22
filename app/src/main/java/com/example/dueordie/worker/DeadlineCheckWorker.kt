package com.example.dueordie.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dueordie.data.local.AppDatabase
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.CompletionHistoryStore
import com.example.dueordie.util.MoraNudgeEngine
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import com.example.dueordie.widget.DueOrDieWidgetProvider
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.ZoneId

class DeadlineCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.taskDao()
            val now = System.currentTimeMillis()
            val prefs = OnboardingPreferences(context)
            val canNotify = prefs.areRemindersEnabled && PermissionHelper.isNotificationPermissionGranted(context)

            // Mark newly overdue tasks
            val markedCount = dao.markOverdueTasks(now)
            if (markedCount > 0) {
                (context.applicationContext as? com.example.dueordie.DueOrDieApplication)
                    ?.notionSyncManager?.triggerBackgroundSync()
            }
            if (markedCount > 0 && canNotify) {
                val allTasks = dao.getAllTasks().firstOrNull().orEmpty()
                val latest = allTasks.filter { it.status == TaskStatus.OVERDUE }.maxByOrNull { it.deadlineTimestamp }
                val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
                    allTasks, now, archivedRecords = CompletionHistoryStore(context).getRecords()
                )
                if (latest != null) {
                    val (title, message) = MoraNudgeEngine.buildOverdueNotificationCopy(latest.title, markedCount, streak)
                    NotificationHelper.showReminderNotification(context, title, message, notificationId = 1002)
                } else NotificationHelper.showOverdueNotification(context, markedCount)
            }

            // Send calm reminders for tasks due within the next 60 minutes
            if (canNotify) {
                val allTasks = dao.getAllTasks().firstOrNull() ?: emptyList()
                val pendingTasks = allTasks.filter { it.status == TaskStatus.PENDING }
                val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
                    allTasks, now, archivedRecords = CompletionHistoryStore(context).getRecords()
                )

                for (task in pendingTasks) {
                    val diffMs = task.deadlineTimestamp - now
                    val minutesLeft = diffMs / (1000 * 60)

                    if (minutesLeft in 1..60) {
                        val (title, alertMessage) = MoraNudgeEngine.buildReminderNotificationCopy(task, minutesLeft, streak)

                        NotificationHelper.showReminderNotification(
                            context = context,
                            title = title,
                            message = alertMessage,
                            notificationId = task.id.toInt() + 2000
                        )
                    }
                }
            }

            // A page synced yesterday as "Not Today" can become "Do Today" at midnight.
            val app = context.applicationContext as? com.example.dueordie.DueOrDieApplication
            val notionPrefs = app?.notionPreferences
            if (app != null && notionPrefs != null && !notionPrefs.tasksDatabaseId.isNullOrBlank() &&
                now - (notionPrefs.lastSyncTimestamp ?: 0L) >= 15 * 60 * 1000L) {
                app.notionSyncManager.pullFromNotion()
            }
            if (notionPrefs != null && !notionPrefs.tasksDatabaseId.isNullOrBlank()) {
                val today = LocalDate.now()
                if (notionPrefs.lastPlanRefreshDate != today.toString()) {
                    val zone = ZoneId.systemDefault()
                    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                    val nextDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val changed = dao.markTodayPlanTasksDirty(start, nextDay)
                    notionPrefs.lastPlanRefreshDate = today.toString()
                    if (changed > 0) app?.notionSyncManager?.triggerBackgroundSync()
                }
            }

            // Update Home Screen AppWidget
            DueOrDieWidgetProvider.updateAllWidgets(context)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
