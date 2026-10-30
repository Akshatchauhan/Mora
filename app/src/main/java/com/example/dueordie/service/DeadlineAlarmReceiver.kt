package com.example.dueordie.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Delivers overdue alarms and schedules the next deadline without relying on a live service. */
class DeadlineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != "com.intellectexplorer.mora.action.DEADLINE_ALARM") return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = appContext as? DueOrDieApplication ?: return@launch
                val now = System.currentTimeMillis()
                app.taskRepository.checkAndUpdateOverdueTasks(now)
                val tasks = app.taskRepository.allTasks.first()
                val preferences = OnboardingPreferences(appContext)

                if (preferences.isOverdueAlarmEnabled &&
                    PermissionHelper.isNotificationPermissionGranted(appContext)
                ) {
                    tasks.asSequence()
                        .filter { it.status != TaskStatus.COMPLETED && it.isOverdueAt(now) }
                        .filterNot { preferences.wasOverdueAlarmShown(it.id, it.deadlineTimestamp) }
                        .forEach { task ->
                            NotificationHelper.showOverdueAlarmNotification(appContext, task)
                            preferences.markOverdueAlarmShown(task.id, task.deadlineTimestamp)
                        }
                }

                DeadlineAlarmScheduler.scheduleNext(
                    context = appContext,
                    tasks = tasks,
                    enabled = preferences.isOverdueAlarmEnabled
                )
                OngoingNotificationService.startOrUpdate(appContext)
            } catch (_: Exception) {
                // A later task/service refresh can schedule another attempt.
            } finally {
                scope.cancel()
                pendingResult.finish()
            }
        }
    }
}
