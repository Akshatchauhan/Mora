package com.example.dueordie.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * System-bound NotificationListenerService that keeps Mora's process bound to Android's
 * system_server so Low Memory Killer (lmkd) on low-RAM devices does not kill the foreground
 * monitor, and automatically respawns OngoingNotificationService if reclaimed.
 * Never reads or stores notification text.
 */
class MoraKeepAliveListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        OngoingNotificationService.startOrUpdate(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                requestRebind(ComponentName(applicationContext, MoraKeepAliveListenerService::class.java))
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // No-op: Mora never reads notification contents.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        fun ensureRebound(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    val cn = ComponentName(context, MoraKeepAliveListenerService::class.java)
                    context.packageManager.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    requestRebind(cn)
                }
            }
        }
    }
}
