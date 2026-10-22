package com.example.dueordie.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dueordie.DueOrDieApplication

class NotionSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as? DueOrDieApplication)?.notionSyncManager
            ?: return Result.failure()
        return if (manager.syncNow()) Result.success() else Result.retry()
    }
}
