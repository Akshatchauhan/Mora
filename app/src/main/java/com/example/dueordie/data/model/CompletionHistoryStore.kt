package com.example.dueordie.data.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight durable record of a completed task's outcome so deleting old completed tasks
 * from the active list does not erase historical on-time rates or consecutive-day streaks.
 */
data class CompletedTaskRecord(
    val taskId: Long,
    val deadlineTimestamp: Long,
    val completedAtTimestamp: Long
) {
    val isCompletedOnTime: Boolean
        get() = completedAtTimestamp <= deadlineTimestamp
}

class CompletionHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecords(): List<CompletedTaskRecord> {
        val rawSet = prefs.getStringSet(KEY_RECORDS, emptySet()) ?: emptySet()
        return rawSet.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 3) {
                val id = parts[0].toLongOrNull()
                val deadline = parts[1].toLongOrNull()
                val completedAt = parts[2].toLongOrNull()
                if (id != null && deadline != null && completedAt != null) {
                    CompletedTaskRecord(id, deadline, completedAt)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    fun recordCompletion(taskId: Long, deadlineTimestamp: Long, completedAtTimestamp: Long) {
        if (taskId <= 0L) return
        val existing = getRecords().filterNot { it.taskId == taskId }
        val updated = (existing + CompletedTaskRecord(taskId, deadlineTimestamp, completedAtTimestamp))
            .map { "${it.taskId}:${it.deadlineTimestamp}:${it.completedAtTimestamp}" }
            .toSet()
        prefs.edit().putStringSet(KEY_RECORDS, updated).apply()
    }

    fun removeRecord(taskId: Long) {
        if (taskId <= 0L) return
        val updated = getRecords()
            .filterNot { it.taskId == taskId }
            .map { "${it.taskId}:${it.deadlineTimestamp}:${it.completedAtTimestamp}" }
            .toSet()
        prefs.edit().putStringSet(KEY_RECORDS, updated).apply()
    }

    companion object {
        private const val PREFS_NAME = "due_or_die_completion_history"
        private const val KEY_RECORDS = "key_completed_task_records"
    }
}
