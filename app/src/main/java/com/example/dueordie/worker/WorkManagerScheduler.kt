package com.example.dueordie.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dueordie.util.OnboardingPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    const val WORK_DEADLINE_CHECK = "work_deadline_check"
    const val WORK_REMINDER_9AM = "work_reminder_9am"
    const val WORK_REMINDER_1PM = "work_reminder_1pm"
    const val WORK_REMINDER_5PM = "work_reminder_5pm"
    const val WORK_REMINDER_9PM = "work_reminder_9pm"

    fun scheduleAllWorkers(context: Context) {
        scheduleDeadlineCheckWorker(context)
        val prefs = OnboardingPreferences(context)
        if (prefs.areRemindersEnabled) {
            scheduleDailyReminders(context)
        } else {
            cancelDailyReminders(context)
        }
    }

    fun scheduleDeadlineCheckWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val deadlineCheckRequest = PeriodicWorkRequestBuilder<DeadlineCheckWorker>(
            15, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_DEADLINE_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            deadlineCheckRequest
        )
    }

    fun scheduleDailyReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val slots = listOf(
            Triple(9, WORK_REMINDER_9AM, "Morning"),
            Triple(13, WORK_REMINDER_1PM, "Afternoon"),
            Triple(17, WORK_REMINDER_5PM, "Evening"),
            Triple(21, WORK_REMINDER_9PM, "Night")
        )

        for ((targetHour, workName, slotLabel) in slots) {
            val initialDelay = calculateInitialDelayForHour(targetHour)
            val data = Data.Builder()
                .putString("SLOT_LABEL", slotLabel)
                .build()

            val reminderRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                reminderRequest
            )
        }
    }

    fun cancelDailyReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_REMINDER_9AM)
        workManager.cancelUniqueWork(WORK_REMINDER_1PM)
        workManager.cancelUniqueWork(WORK_REMINDER_5PM)
        workManager.cancelUniqueWork(WORK_REMINDER_9PM)
    }

    fun calculateInitialDelayForHour(targetHour: Int, nowCalendar: Calendar = Calendar.getInstance()): Long {
        val targetCalendar = Calendar.getInstance().apply {
            timeInMillis = nowCalendar.timeInMillis
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (targetCalendar.before(nowCalendar)) {
            targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return targetCalendar.timeInMillis - nowCalendar.timeInMillis
    }
}
