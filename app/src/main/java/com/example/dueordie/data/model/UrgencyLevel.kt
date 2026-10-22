package com.example.dueordie.data.model

enum class UrgencyLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    OVERDUE;

    val label: String
        get() = when (this) {
            LOW -> "Low Urgency"
            MEDIUM -> "Medium Urgency"
            HIGH -> "High Urgency"
            CRITICAL -> "Critical!"
            OVERDUE -> "Overdue!"
        }

    val badgeText: String
        get() = when (this) {
            LOW -> "LOW"
            MEDIUM -> "MED"
            HIGH -> "HIGH"
            CRITICAL -> "DUE SOON"
            OVERDUE -> "OVERDUE"
        }
}

data class UrgencySummary(
    val totalPending: Int = 0,
    val overdueCount: Int = 0,
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val mediumCount: Int = 0,
    val lowCount: Int = 0
) {
    val highestUrgency: UrgencyLevel
        get() = when {
            overdueCount > 0 -> UrgencyLevel.OVERDUE
            criticalCount > 0 -> UrgencyLevel.CRITICAL
            highCount > 0 -> UrgencyLevel.HIGH
            mediumCount > 0 -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }
}
