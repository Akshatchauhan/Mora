package com.example.dueordie.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.InteractionModePolicy
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (action == ACTION_SILENCE) {
            if (notificationId != -1) notificationManager?.cancel(notificationId)
            else if (taskId != -1L) NotificationHelper.cancelOverdueAlarmNotification(context, taskId)
            return
        }
        if (taskId == -1L) return

        val app = context.applicationContext as? DueOrDieApplication ?: return
        val repository = app.taskRepository
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            var actionApplied = false
            try {
                val task = repository.getTaskByIdOneShot(taskId)
                if (task != null) {
                    when (action) {
                        ACTION_MARK_DONE -> {
                            val now = System.currentTimeMillis()
                            repository.updateTask(
                                task.copy(
                                    status = TaskStatus.COMPLETED,
                                    completedAtTimestamp = now
                                )
                            )
                            beginFocusCooldownIfNeeded(task, now, context)
                            actionApplied = true
                        }
                        ACTION_RESCHEDULE -> {
                            val prefs = OnboardingPreferences(context)
                            if (!InteractionModePolicy.canReschedule(prefs.appInteractionMode)) {
                                // Strict Mode: Rescheduling is strictly forbidden! Cancel stale notification.
                                if (notificationId != -1) notificationManager?.cancel(notificationId)
                                else NotificationHelper.cancelOverdueAlarmNotification(context, taskId)
                                OnboardingPreferences(context).clearOverdueAlarmShown(taskId)
                                return@launch
                            }
                            val now = System.currentTimeMillis()
                            val newDeadline = maxOf(now, task.deadlineTimestamp) + 3_600_000L
                            repository.updateTask(
                                task.copy(
                                    deadlineTimestamp = newDeadline,
                                    status = TaskStatus.PENDING
                                )
                            )
                            beginFocusCooldownIfNeeded(task, now, context)
                            actionApplied = true
                        }
                    }
                }
                if (actionApplied) {
                    if (notificationId != -1) notificationManager?.cancel(notificationId)
                    else NotificationHelper.cancelOverdueAlarmNotification(context, taskId)
                    OnboardingPreferences(context).clearOverdueAlarmShown(taskId)
                    OngoingNotificationService.startOrUpdate(context)
                }
            } catch (_: Exception) {
                // Keep the alert visible if persistence fails so the user can retry.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun beginFocusCooldownIfNeeded(task: Task, nowMs: Long, context: Context) {
        val prefs = OnboardingPreferences(context)
        if (task.isOverdueAt(nowMs) && (task.isHostageModeEnabled || prefs.isGlobalHostageModeEnabled)) {
            prefs.overdueCooldownUntilWallMs = nowMs + OnboardingPreferences.OVERDUE_UNBLOCK_COOLDOWN_MS
            prefs.lastCooldownTaskTitle = task.title
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.example.dueordie.action.TASK_MARK_DONE"
        const val ACTION_RESCHEDULE = "com.example.dueordie.action.TASK_RESCHEDULE"
        const val ACTION_SILENCE = "com.example.dueordie.action.TASK_SILENCE"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
