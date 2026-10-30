package com.example.dueordie

import android.app.Application
import com.example.dueordie.data.local.AppDatabase
import com.example.dueordie.data.repository.TaskRepository
import com.example.dueordie.data.repository.TaskRepositoryImpl
import com.example.dueordie.data.notion.NotionPreferences
import com.example.dueordie.data.notion.NotionSyncManager
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.worker.WorkManagerScheduler
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel

class DueOrDieApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val notionPreferences: NotionPreferences by lazy { NotionPreferences(this) }
    val notionSyncManager: NotionSyncManager by lazy { NotionSyncManager(this, database.taskDao(), notionPreferences) }
    val taskRepository: TaskRepository by lazy { TaskRepositoryImpl(database.taskDao(), notionSyncManager) }

    override fun onCreate() {
        super.onCreate()
        // Initialize Microsoft Clarity officially at Application startup
        val clarityConfig = ClarityConfig(
            projectId = "z4f5ufifl3",
            logLevel = LogLevel.Verbose
        )
        Clarity.initialize(applicationContext, clarityConfig)

        NotificationHelper.createNotificationChannel(this)
        WorkManagerScheduler.scheduleAllWorkers(this)
    }
}
