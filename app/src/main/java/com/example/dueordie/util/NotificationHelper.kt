package com.example.dueordie.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.dueordie.R
import com.example.dueordie.MainActivity
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskPriority
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.service.TaskActionReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_REMINDERS_ID = "dueordie_task_reminders"
    const val CHANNEL_REMINDERS_NAME = "Task Reminders & Deadline Alerts"
    const val CHANNEL_REMINDERS_DESC = "Notifications for due tasks, daily check-ins, and deadline alerts."

    const val CHANNEL_ONGOING_ID = "dueordie_ongoing_tasks"
    const val CHANNEL_ONGOING_NAME = "Active Tasks & Sticky Countdown"
    const val CHANNEL_ONGOING_DESC = "Sticky ongoing status bar notification displaying the next active task and deadline countdown."

    const val CHANNEL_ALARM_ID = "dueordie_overdue_alarms"
    const val CHANNEL_ALARM_NAME = "Overdue Task Alarms"
    const val CHANNEL_ALARM_DESC = "Audible alarm alert ringing when a task deadline has passed without completion."

    const val ONGOING_NOTIFICATION_ID = 1003
    const val NOTIFICATION_ID_DAILY_CHECKIN = 1005
    private const val ALARM_NOTIFICATION_PREFIX = 4000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS_ID,
                CHANNEL_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_REMINDERS_DESC
                enableVibration(true)
            }

            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                CHANNEL_ONGOING_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_ONGOING_DESC
                enableVibration(false)
                setSound(null, null)
            }

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                CHANNEL_ALARM_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_ALARM_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
                setSound(alarmSound, audioAttributes)
            }

            notificationManager.createNotificationChannel(remindersChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    // Kept for backward compatibility
    fun createNotificationChannel(context: Context) {
        createNotificationChannels(context)
    }

    fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = 1001
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.drawable.ic_nav_tasks)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun showOverdueNotification(
        context: Context,
        overdueCount: Int,
        sampleTaskTitle: String? = null
    ) {
        val title = if (overdueCount == 1) "Task past due" else "Tasks past due"
        val message = if (overdueCount == 1 && sampleTaskTitle != null) {
            "'$sampleTaskTitle' is past its due time."
        } else if (overdueCount == 1) {
            "You have 1 task past its due time."
        } else {
            "You have $overdueCount tasks past their due time."
        }
        showReminderNotification(context, title, message, notificationId = 1002)
    }

    /**
     * Sounds an audible overdue alarm notification when enabled by the user.
     * Provides immediate actions to Mark done, Reschedule (+1h), or Silence the alert.
     */
    fun showOverdueAlarmNotification(context: Context, task: Task) {
        val prefs = OnboardingPreferences(context)
        if (!prefs.isOverdueAlarmEnabled || !PermissionHelper.isNotificationPermissionGranted(context)) return

        createNotificationChannels(context)
        val notificationId = (ALARM_NOTIFICATION_PREFIX + (task.id % 10000)).toInt()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_MARK_DONE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rescheduleIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_RESCHEDULE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val reschedulePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silenceIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_SILENCE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val silencePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val deadlineStr = formatTimeOrDate(task.deadlineTimestamp)
        val isStrictMode = prefs.appInteractionMode == com.example.dueordie.util.AppInteractionMode.STRICT
        val title = "Overdue: ${task.title}"
        val body = "Deadline passed ($deadlineStr)."
        val bigBody = if (isStrictMode) {
            "The deadline for \"${task.title}\" passed ($deadlineStr). Focus Lock is active."
        } else {
            "The deadline for \"${task.title}\" passed ($deadlineStr)."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM_ID)
            .setSmallIcon(R.drawable.ic_nav_tasks)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 600, 250, 600, 250, 600))
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)

        if (!isStrictMode) {
            builder.addAction(0, "Reschedule (+1h)", reschedulePendingIntent)
        }
        builder.addAction(0, "Silence", silencePendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Cancel any pending due-soon reminders for this task so notifications do not stack
        notificationManager.cancel(task.id.toInt() + 2000)
        notificationManager.cancel(1002)
        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelOverdueAlarmNotification(context: Context, taskId: Long) {
        val notificationId = (ALARM_NOTIFICATION_PREFIX + (taskId % 10000)).toInt()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        OnboardingPreferences(context).clearOverdueAlarmShown(taskId)
    }

    /**
     * Builds sticky ongoing notification for active tasks with priority hierarchy,
     * quiet Mora aesthetics, and direct interactive quick actions.
     */
    fun buildOngoingNotification(
        context: Context,
        activeTasks: List<Task>,
        nowMs: Long = System.currentTimeMillis()
    ): Notification {
        createNotificationChannels(context)

        val pendingTasks = activeTasks.filter { it.status != TaskStatus.COMPLETED }
            .sortedWith(
                compareByDescending<Task> { it.priority.level }
                    .thenBy { it.deadlineTimestamp }
            )
        val nextTask = pendingTasks.firstOrNull()

        val title: String
        val contentText: String
        val bigText: String

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_nav_tasks)
            .setSubText("Mora")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        if (nextTask != null) {
            val countdownStr = formatDeadlineCountdown(nextTask.deadlineTimestamp, nowMs)
            val deadlineTimeStr = formatTimeOrDate(nextTask.deadlineTimestamp)
            val priorityTag = "[${nextTask.priority.displayName}]"

            title = "${nextTask.title} $priorityTag"
            contentText = "$countdownStr, $deadlineTimeStr (${pendingTasks.size} active)"

            bigText = buildString {
                append("Next: ${nextTask.title} $priorityTag\n")
                append("Due: $deadlineTimeStr ($countdownStr)\n")
                append("Active tasks: ${pendingTasks.size}")
                if (pendingTasks.size > 1) {
                    append("\n\nUpcoming tasks:")
                    pendingTasks.drop(1).take(3).forEach { t ->
                        val tCountdown = formatDeadlineCountdown(t.deadlineTimestamp, nowMs)
                        append("\n- ${t.title} [${t.priority.displayName}] ($tCountdown)")
                    }
                    if (pendingTasks.size > 4) {
                        append("\n+ ${pendingTasks.size - 4} more")
                    }
                }
            }

            // Quick interactive action: Reschedule +1 Hour directly from notification
            val rescheduleIntent = Intent(context, TaskActionReceiver::class.java).apply {
                action = TaskActionReceiver.ACTION_RESCHEDULE
                putExtra(TaskActionReceiver.EXTRA_TASK_ID, nextTask.id)
            }
            val reschedulePendingIntent = PendingIntent.getBroadcast(
                context,
                (nextTask.id % 10000).toInt() + 1000,
                rescheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "+1 Hour", reschedulePendingIntent)

            builder.setPriority(
                if (nextTask.priority == TaskPriority.HIGH) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW
            )
        } else {
            title = "Mora"
            contentText = "No active tasks pending. Great job!"
            bigText = "No active tasks pending. Create a new task to track deadlines."
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        return builder
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .build()
    }

    /**
     * Shows or updates the sticky ongoing notification.
     * If no active tasks exist, cancels the ongoing notification.
     */
    fun updateOngoingNotification(
        context: Context,
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val activeTasks = tasks.filter { it.status != TaskStatus.COMPLETED }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (activeTasks.isNotEmpty()) {
            val notification = buildOngoingNotification(context, activeTasks, nowMs)
            notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        } else {
            notificationManager.cancel(ONGOING_NOTIFICATION_ID)
        }
    }

    /**
     * Cancels the sticky ongoing notification.
     */
    fun cancelOngoingNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ONGOING_NOTIFICATION_ID)
    }

    /**
     * Formats duration in milliseconds into a concise string (e.g. "2d 5h", "3h 12m", "45m", "< 1m").
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val totalMinutes = totalSeconds / 60
        val totalHours = totalMinutes / 60
        val days = totalHours / 24
        val hours = totalHours % 24
        val minutes = totalMinutes % 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    /**
     * Formats deadline timestamp into a countdown string relative to now.
     */
    fun formatDeadlineCountdown(
        deadlineMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val diff = deadlineMs - nowMs
        return if (diff < 0) {
            "Overdue by ${formatDuration(-diff)}"
        } else {
            "Due in ${formatDuration(diff)}"
        }
    }

    /**
     * Formats timestamp into readable date time format.
     */
    fun formatTimeOrDate(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}
