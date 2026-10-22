package com.example.dueordie.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * High-performance, zero-latency accessibility service for Mora Mindful Pause.
 * Listens for window state changes to detect app switches immediately without waiting
 * for polling loops. Never reads or inspects screen content or personal text.
 */
class MoraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        OngoingNotificationService.startOrUpdate(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            OngoingNotificationService.notifyForegroundAppChanged(applicationContext, pkg)
        }
    }

    override fun onInterrupt() {
        // Accessibility interrupted
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
