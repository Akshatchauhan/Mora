package com.example.dueordie.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatUtils {

    fun formatCountdown(deadlineMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMs = deadlineMs - nowMs
        if (diffMs <= 0) {
            val overdueMs = -diffMs
            val totalSecs = overdueMs / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return when {
                hours > 24 -> {
                    val days = hours / 24
                    "OVERDUE by ${days}d ${hours % 24}h"
                }
                hours > 0 -> String.format(Locale.US, "OVERDUE by %02dh %02dm %02ds", hours, minutes, seconds)
                minutes > 0 -> String.format(Locale.US, "OVERDUE by %02dm %02ds", minutes, seconds)
                else -> String.format(Locale.US, "OVERDUE by %02ds", seconds)
            }
        } else {
            val totalSecs = diffMs / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return when {
                hours > 24 -> {
                    val days = hours / 24
                    "${days}d ${hours % 24}h remaining"
                }
                hours > 0 -> String.format(Locale.US, "%02dh %02dm %02ds remaining", hours, minutes, seconds)
                minutes > 0 -> String.format(Locale.US, "%02dm %02ds remaining", minutes, seconds)
                else -> String.format(Locale.US, "%02ds remaining", seconds)
            }
        }
    }

    fun formatRemainingCountdown(deadlineMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMs = deadlineMs - nowMs
        if (diffMs <= 0) {
            val overdueMs = -diffMs
            val totalMinutes = overdueMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours >= 24 -> {
                    val days = hours / 24
                    "Past due by ${days}d ${hours % 24}h"
                }
                hours > 0 -> "Past due by ${hours}h ${minutes}m"
                minutes > 0 -> "Past due by ${minutes}m"
                else -> "Past due (< 1m)"
            }
        } else {
            val totalMinutes = diffMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours >= 24 -> {
                    val days = hours / 24
                    "Due in ${days}d ${hours % 24}h"
                }
                hours > 0 -> "Due in ${hours}h ${minutes}m"
                minutes > 0 -> "Due in ${minutes}m"
                else -> "Due in < 1m"
            }
        }
    }

    fun formatDeadlineDateTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatDateTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatDateOnly(timestampMs: Long): String {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatTimeOnly(timestampMs: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatScreenTime(durationMs: Long): String {
        val totalMinutes = durationMs / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours hr${if (hours > 1) "s" else ""}, $minutes min${if (minutes > 1) "s" else ""}"
            hours > 0 -> "$hours hr${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes min${if (minutes > 1) "s" else ""}"
            else -> "0 mins"
        }
    }
}
