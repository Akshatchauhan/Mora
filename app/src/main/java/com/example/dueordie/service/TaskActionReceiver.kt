package com.example.dueordie.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.TaskStatus
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
        if (notificationId != -1) {
            notificationManager?.cancel(notificationId)
        } else if (taskId != -1L) {
            NotificationHelper.cancelOverdueAlarmNotification(context, taskId)
        }

        if (taskId == -1L && action != ACTION_SILENCE) return

        val app = context.applicationContext as? DueOrDieApplication ?: return
        val repository = app.taskRepository

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                ACTION_MARK_DONE -> {
                    val task = repository.getTaskByIdOneShot(taskId)
                    if (task != null) {
                        repository.updateTask(
                            task.copy(
                                status = TaskStatus.COMPLETED,
                                completedAtTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
                ACTION_RESCHEDULE -> {
                    val task = repository.getTaskByIdOneShot(taskId)
                    if (task != null) {
                        val newDeadline = maxOf(System.currentTimeMillis(), task.deadlineTimestamp) + 3_600_000L
                        repository.updateTask(
                            task.copy(
                                deadlineTimestamp = newDeadline,
                                status = TaskStatus.PENDING
                            )
                        )
                    }
                }
                ACTION_SILENCE -> {
                    // Notification already cancelled above
                }
            }
            OngoingNotificationService.startOrUpdate(context)
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
