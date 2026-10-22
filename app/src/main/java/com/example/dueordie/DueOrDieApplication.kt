package com.example.dueordie

import android.app.Application
import com.example.dueordie.data.local.AppDatabase
import com.example.dueordie.data.repository.TaskRepository
import com.example.dueordie.data.repository.TaskRepositoryImpl
import com.example.dueordie.data.notion.NotionPreferences
import com.example.dueordie.data.notion.NotionSyncManager
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.worker.WorkManagerScheduler

class DueOrDieApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val notionPreferences: NotionPreferences by lazy { NotionPreferences(this) }
    val notionSyncManager: NotionSyncManager by lazy { NotionSyncManager(this, database.taskDao(), notionPreferences) }
    val taskRepository: TaskRepository by lazy { TaskRepositoryImpl(database.taskDao(), notionSyncManager) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        WorkManagerScheduler.scheduleAllWorkers(this)
    }
}
