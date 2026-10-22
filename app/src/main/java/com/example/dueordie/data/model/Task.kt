package com.example.dueordie.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val deadlineTimestamp: Long,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val completedAtTimestamp: Long? = null,
    val requirePhotoProof: Boolean = false,
    val photoPath: String? = null,
    val isHostageModeEnabled: Boolean = false,
    val notionPageId: String? = null,
    val notionSyncDirty: Boolean = true,
    val lastSyncedAtTimestamp: Long? = null
) {
    fun calculateUrgencyLevel(nowMs: Long = System.currentTimeMillis()): UrgencyLevel {
        if (status == TaskStatus.COMPLETED) return UrgencyLevel.LOW
        if (status == TaskStatus.OVERDUE || (deadlineTimestamp <= nowMs)) return UrgencyLevel.OVERDUE
        val diffMillis = deadlineTimestamp - nowMs
        val hoursRemaining = diffMillis.toDouble() / (1000 * 60 * 60)
        return when {
            hoursRemaining < 3 -> UrgencyLevel.CRITICAL
            hoursRemaining < 12 -> UrgencyLevel.HIGH
            hoursRemaining < 24 -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }
    }

    fun isOverdueAt(nowMs: Long = System.currentTimeMillis()): Boolean {
        return status == TaskStatus.OVERDUE || (status == TaskStatus.PENDING && deadlineTimestamp <= nowMs)
    }

    val isOverdue: Boolean
        get() = isOverdueAt(System.currentTimeMillis())

    val isCompletedOnTime: Boolean
        get() = TaskDomainRules.isCompletedOnTime(this)

    val isCompletedLate: Boolean
        get() = TaskDomainRules.isCompletedLate(this)
}
