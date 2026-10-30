package com.example.dueordie.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper

/** Schedules the next deadline independently of the foreground service's lifetime. */
object DeadlineAlarmScheduler {
    private const val REQUEST_CODE = 7301
    private const val ACTION_DEADLINE_ALARM = "com.intellectexplorer.mora.action.DEADLINE_ALARM"

    fun scheduleNext(context: Context, tasks: List<Task>, enabled: Boolean) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        val now = System.currentTimeMillis()
        val preferences = OnboardingPreferences(appContext)
        val hasUnannouncedOverdueTask = PermissionHelper.isNotificationPermissionGranted(appContext) &&
            tasks.any { task ->
                task.status != TaskStatus.COMPLETED && task.isOverdueAt(now) &&
                    !preferences.wasOverdueAlarmShown(task.id, task.deadlineTimestamp)
            }
        val triggerAt = if (hasUnannouncedOverdueTask) {
            now + 1_000L
        } else {
            tasks.asSequence()
                .filter { it.status == TaskStatus.PENDING && it.deadlineTimestamp > now }
                .minOfOrNull { it.deadlineTimestamp }
        } ?: return

        val triggerAtMillis = triggerAt.coerceAtLeast(now + 250L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Persists across process death and can wake during Doze; Android may defer it.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    internal fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, DeadlineAlarmReceiver::class.java).setAction(ACTION_DEADLINE_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
