package com.example.dueordie.data.model

enum class TaskPriority(val level: Int, val displayName: String) {
    HIGH(3, "High"),
    MEDIUM(2, "Medium"),
    LOW(1, "Low");

    companion object {
        fun fromDisplayName(name: String?): TaskPriority {
            if (name == null) return MEDIUM
            return entries.firstOrNull {
                it.displayName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true)
            } ?: MEDIUM
        }

        fun fromString(name: String?): TaskPriority {
            if (name == null) return MEDIUM
            return runCatching { valueOf(name) }.getOrElse { fromDisplayName(name) }
        }
    }
}
