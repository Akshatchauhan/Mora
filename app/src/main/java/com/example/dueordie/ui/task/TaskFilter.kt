package com.example.dueordie.ui.task

enum class TaskFilter(val title: String) {
    ALL("All"),
    PENDING("To do"),
    OVERDUE("Late"),
    COMPLETED("Done");

    val displayName: String
        get() = title
}
