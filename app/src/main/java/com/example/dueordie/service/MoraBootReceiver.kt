package com.example.dueordie.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dueordie.worker.WorkManagerScheduler

/**
 * Ensures Mora's workers and background monitor restart automatically on phone reboot
 * or app update without requiring the user to manually reopen the app.
 */
class MoraBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WorkManagerScheduler.scheduleAllWorkers(context)
            OngoingNotificationService.startOrUpdate(context)
            MoraKeepAliveListenerService.ensureRebound(context)
        }
    }
}
