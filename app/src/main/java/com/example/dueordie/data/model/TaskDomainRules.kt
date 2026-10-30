package com.example.dueordie.data.model

import java.io.File
import java.util.Calendar
import java.util.TimeZone

data class FocusLockDecision(
    val shouldIntervene: Boolean,
    val blockingTask: Task? = null,
    val overdueMinutes: Long = 0L,
    val isCooldownActive: Boolean = false,
    val cooldownRemainingMinutes: Int = 0
)

data class DayActivity(
    val dayOfWeek: String,
    val dayOfMonth: Int,
    val completedCount: Int,
    val onTimeCount: Int,
    val isToday: Boolean
)

object TaskDomainRules {

    /**
     * Determines the correct [TaskStatus] when creating or editing a task deadline.
     * - Completed tasks remain [TaskStatus.COMPLETED].
     * - Incomplete tasks with a deadline at or before [nowMs] become [TaskStatus.OVERDUE].
     * - Incomplete tasks with a future deadline become [TaskStatus.PENDING] (allowing
     *   previously overdue tasks to be rescheduled back to pending).
     */
    fun determineStatusForDeadline(
        currentStatus: TaskStatus?,
        deadlineTimestamp: Long,
        nowMs: Long = System.currentTimeMillis()
    ): TaskStatus {
        if (currentStatus == TaskStatus.COMPLETED) {
            return TaskStatus.COMPLETED
        }
        return if (deadlineTimestamp <= nowMs) {
            TaskStatus.OVERDUE
        } else {
            TaskStatus.PENDING
        }
    }

    /**
     * Resolves an edited [Task] while freezing historical deadline and completion timestamps
     * if the task is already [TaskStatus.COMPLETED], preventing subsequent edits from rewriting
     * historical on-time performance.
     */
    fun resolveEditedTask(
        existingTask: Task,
        newTitle: String,
        newDescription: String,
        requestedDeadlineTimestamp: Long,
        requirePhotoProof: Boolean = false,
        effectivePhotoPath: String? = null,
        isHostageModeEnabled: Boolean = false,
        priority: TaskPriority = existingTask.priority,
        nowMs: Long = System.currentTimeMillis(),
        canEditDeadline: Boolean = true
    ): Task {
        val isAlreadyCompleted = existingTask.status == TaskStatus.COMPLETED
        val finalDeadline = if (!canEditDeadline || isAlreadyCompleted) {
            existingTask.deadlineTimestamp
        } else {
            requestedDeadlineTimestamp
        }
        val finalPriority = if (canEditDeadline) priority else existingTask.priority
        val finalStatus = determineStatusForDeadline(
            currentStatus = existingTask.status,
            deadlineTimestamp = finalDeadline,
            nowMs = nowMs
        )
        return existingTask.copy(
            title = newTitle.trim(),
            description = newDescription.trim(),
            deadlineTimestamp = finalDeadline,
            priority = finalPriority,
            requirePhotoProof = requirePhotoProof,
            photoPath = effectivePhotoPath,
            isHostageModeEnabled = isHostageModeEnabled,
            status = finalStatus,
            completedAtTimestamp = existingTask.completedAtTimestamp
        )
    }

    /**
     * Returns true if the task was completed on or before its deadline.
     */
    fun isCompletedOnTime(task: Task): Boolean {
        val completedAt = task.completedAtTimestamp ?: return false
        return task.status == TaskStatus.COMPLETED && completedAt <= task.deadlineTimestamp
    }

    /**
     * Returns true if the task was completed after its deadline (or completed without a timestamp).
     */
    fun isCompletedLate(task: Task): Boolean {
        if (task.status != TaskStatus.COMPLETED) return false
        val completedAt = task.completedAtTimestamp ?: return true
        return completedAt > task.deadlineTimestamp
    }

    /**
     * Calculates the on-time completion percentage across all tasks that have either been
     * completed (on time or late, including durable [archivedRecords] for deleted completed tasks)
     * or are currently overdue.
     *
     * Returns `null` when no tasks have been completed or become overdue yet.
     */
    fun calculateOnTimeRate(
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis(),
        archivedRecords: List<CompletedTaskRecord> = emptyList()
    ): Int? {
        var onTimeCount = 0
        var lateCompletedCount = 0
        var activeOverdueCount = 0
        val seenCompletedIds = HashSet<Long>()

        for (task in tasks) {
            when {
                isCompletedOnTime(task) -> {
                    onTimeCount++
                    if (task.id > 0L) seenCompletedIds.add(task.id)
                }
                isCompletedLate(task) -> {
                    lateCompletedCount++
                    if (task.id > 0L) seenCompletedIds.add(task.id)
                }
                task.isOverdueAt(nowMs) -> activeOverdueCount++
            }
        }

        for (record in archivedRecords) {
            if (record.taskId > 0L && record.taskId in seenCompletedIds) continue
            if (record.isCompletedOnTime) {
                onTimeCount++
            } else {
                lateCompletedCount++
            }
        }

        val totalCompleted = onTimeCount + lateCompletedCount
        if (totalCompleted == 0) return null

        val totalEvaluated = totalCompleted + activeOverdueCount
        return ((onTimeCount.toDouble() / totalEvaluated.toDouble()) * 100.0).toInt()
    }

    /**
     * Computes the current consecutive-day streak of on-time task completions in [timeZone],
     * combining active [tasks] and durable [archivedRecords].
     */
    fun calculateConsecutiveOnTimeStreak(
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        archivedRecords: List<CompletedTaskRecord> = emptyList()
    ): Int {
        val activeOnTimeTimestamps = tasks
            .asSequence()
            .filter { isCompletedOnTime(it) }
            .mapNotNull { it.completedAtTimestamp }

        val archivedOnTimeTimestamps = archivedRecords
            .asSequence()
            .filter { it.isCompletedOnTime }
            .map { it.completedAtTimestamp }

        val completedDayKeys = (activeOnTimeTimestamps + archivedOnTimeTimestamps)
            .map { dayKey(it, timeZone) }
            .toSet()

        if (completedDayKeys.isEmpty()) return 0

        val cursor = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayKey = dayKeyFromCalendar(cursor)
        if (!completedDayKeys.contains(todayKey)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayKey = dayKeyFromCalendar(cursor)
            if (!completedDayKeys.contains(yesterdayKey)) {
                return 0
            }
        }

        var streak = 0
        while (completedDayKeys.contains(dayKeyFromCalendar(cursor))) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun calculateWeeklyCompletionActivity(
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        archivedRecords: List<CompletedTaskRecord> = emptyList()
    ): List<DayActivity> {
        val seenCompletedIds = HashSet<Long>()
        val allCompleted = mutableListOf<Pair<Long, Boolean>>()

        for (task in tasks) {
            val ts = task.completedAtTimestamp
            if (task.status == TaskStatus.COMPLETED && ts != null) {
                allCompleted.add(ts to isCompletedOnTime(task))
                if (task.id > 0L) seenCompletedIds.add(task.id)
            }
        }
        for (rec in archivedRecords) {
            if (rec.taskId > 0L && rec.taskId in seenCompletedIds) continue
            allCompleted.add(rec.completedAtTimestamp to rec.isCompletedOnTime)
        }

        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
        val todayYear = todayCal.get(Calendar.YEAR)

        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return dayNames.mapIndexed { index, name ->
            val dayStart = cal.timeInMillis + (index * 86_400_000L)
            val dayEnd = dayStart + 86_400_000L
            val dayCal = Calendar.getInstance(timeZone).apply { timeInMillis = dayStart }
            val isToday = dayCal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && dayCal.get(Calendar.YEAR) == todayYear

            val inDay = allCompleted.filter { it.first in dayStart until dayEnd }
            DayActivity(
                dayOfWeek = name,
                dayOfMonth = dayCal.get(Calendar.DAY_OF_MONTH),
                completedCount = inDay.size,
                onTimeCount = inDay.count { it.second },
                isToday = isToday
            )
        }
    }

    private fun dayKey(timestampMs: Long, timeZone: TimeZone): Int {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = timestampMs
        }
        return dayKeyFromCalendar(cal)
    }

    private fun dayKeyFromCalendar(cal: Calendar): Int {
        val year = cal.get(Calendar.YEAR)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        return year * 1000 + dayOfYear
    }

    /**
     * Returns true only when there is at least one incomplete, overdue task that has its
     * individual Focus Lock (`isHostageModeEnabled`) switch enabled.
     */
    fun hasQualifyingFocusLockTasks(
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return tasks.any {
            it.status != TaskStatus.COMPLETED &&
                it.isOverdueAt(nowMs) &&
                it.isHostageModeEnabled
        }
    }

    /**
     * Returns true only when Focus Lock is globally enabled, permission is granted, and at least
     * one qualifying overdue task with `isHostageModeEnabled == true` exists.
     */
    fun shouldRunFocusLockMonitor(
        isGlobalFocusLockEnabled: Boolean,
        hasPermission: Boolean,
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis(),
        cooldownUntilMs: Long = 0L
    ): Boolean {
        return isGlobalFocusLockEnabled &&
            hasPermission &&
            (hasQualifyingFocusLockTasks(tasks, nowMs) || cooldownUntilMs > nowMs)
    }

    /**
     * Evaluates whether Focus Lock should intervene for the current foreground app.
     * Only considers incomplete, overdue tasks where `isHostageModeEnabled == true`.
     */
    fun evaluateFocusLock(
        isGlobalFocusLockEnabled: Boolean,
        hasUsagePermission: Boolean,
        foregroundPackage: String?,
        selfPackage: String,
        blockedPackages: Set<String>,
        tasks: List<Task>,
        nowMs: Long = System.currentTimeMillis(),
        cooldownUntilMs: Long = 0L,
        cooldownTaskTitle: String = "Recently completed task"
    ): FocusLockDecision {
        if (!isGlobalFocusLockEnabled || !hasUsagePermission) {
            return FocusLockDecision(shouldIntervene = false)
        }
        if (foregroundPackage.isNullOrBlank() || foregroundPackage == selfPackage) {
            return FocusLockDecision(shouldIntervene = false)
        }
        if (!blockedPackages.contains(foregroundPackage)) {
            return FocusLockDecision(shouldIntervene = false)
        }

        val qualifyingOverdueTasks = tasks.filter {
            it.status != TaskStatus.COMPLETED &&
                it.isOverdueAt(nowMs) &&
                it.isHostageModeEnabled
        }
        if (qualifyingOverdueTasks.isEmpty()) {
            if (cooldownUntilMs > nowMs) {
                val remaining = ((cooldownUntilMs - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                val placeholder = Task(
                    id = -1L,
                    title = cooldownTaskTitle.ifBlank { "Recently completed task" },
                    description = "Focus cooldown active",
                    deadlineTimestamp = nowMs - remaining * 60_000L,
                    status = TaskStatus.OVERDUE,
                    isHostageModeEnabled = true
                )
                return FocusLockDecision(true, placeholder, remaining.toLong(), true, remaining)
            }
            return FocusLockDecision(shouldIntervene = false)
        }

        val blockingTask = qualifyingOverdueTasks.minBy { it.deadlineTimestamp }
        val overdueMinutes = maxOf(1L, (nowMs - blockingTask.deadlineTimestamp) / (60 * 1000L))
        return FocusLockDecision(
            shouldIntervene = true,
            blockingTask = blockingTask,
            overdueMinutes = overdueMinutes
        )
    }

    /**
     * Safely deletes an attached task photo file from local storage if it exists.
     */
    fun deletePhotoFileIfPresent(photoPath: String?): Boolean {
        if (photoPath.isNullOrBlank()) return false
        return runCatching {
            val file = File(photoPath)
            if (file.exists() && file.isFile) {
                file.delete()
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
