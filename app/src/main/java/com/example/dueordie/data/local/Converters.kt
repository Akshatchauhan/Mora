package com.example.dueordie.data.local

import androidx.room.TypeConverter
import com.example.dueordie.data.model.TaskPriority
import com.example.dueordie.data.model.TaskStatus

class Converters {
    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String {
        return status.name
    }

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus {
        return TaskStatus.fromString(value)
    }

    @TypeConverter
    fun fromTaskPriority(priority: TaskPriority?): String {
        return (priority ?: TaskPriority.MEDIUM).name
    }

    @TypeConverter
    fun toTaskPriority(value: String?): TaskPriority {
        return TaskPriority.fromString(value)
    }
}
