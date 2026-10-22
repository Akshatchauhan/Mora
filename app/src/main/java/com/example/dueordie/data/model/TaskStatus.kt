package com.example.dueordie.data.model

enum class TaskStatus {
    PENDING,
    COMPLETED,
    OVERDUE;

    companion object {
        fun fromString(value: String): TaskStatus {
            return try {
                valueOf(value)
            } catch (e: Exception) {
                PENDING
            }
        }
    }
}
