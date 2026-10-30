package com.example.dueordie.data.model

import com.example.dueordie.util.AppInteractionMode

/**
 * Authoritative policy layer for Mora operating modes (Casual vs Strict).
 *
 * One Mora visual system; two clear interaction contracts; one authoritative policy layer.
 * All UI screens, ViewModel actions, and notification receivers validate through this policy.
 */
object InteractionModePolicy {

    const val STRICT_HOLD_DURATION_MS = 1200L

    /**
     * Determines whether an existing saved task's commitment (deadline / core properties)
     * can be changed.
     */
    fun canEditTask(mode: AppInteractionMode, isTaskSaved: Boolean): Boolean {
        if (!isTaskSaved) return true
        return mode == AppInteractionMode.CASUAL
    }

    /**
     * Determines whether a task deadline can be rescheduled / postponed.
     */
    fun canReschedule(mode: AppInteractionMode): Boolean {
        return mode == AppInteractionMode.CASUAL
    }

    /**
     * Determines whether task completion requires a sustained physical hold
     * rather than a single tap.
     */
    fun isHoldToCompleteRequired(mode: AppInteractionMode): Boolean {
        return mode == AppInteractionMode.STRICT
    }

    /**
     * Duration in milliseconds required to hold a task to complete in the given mode.
     */
    fun getRequiredHoldDurationMs(mode: AppInteractionMode): Long {
        return if (mode == AppInteractionMode.STRICT) STRICT_HOLD_DURATION_MS else 0L
    }
}
