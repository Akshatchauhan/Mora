package com.example.dueordie.data.notion

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dueordie.data.local.TaskDao
import com.example.dueordie.worker.NotionSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotionSyncManager(
    context: Context,
    private val taskDao: TaskDao,
    val preferences: NotionPreferences,
    private val api: NotionApiClient = NotionApiClient()
) : NotionSyncTrigger {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    override fun triggerBackgroundSync() {
        if (preferences.integrationToken.isBlank() || preferences.tasksDatabaseId.isNullOrBlank() || !preferences.isAutoSyncEnabled) return
        scope.launch { syncNow() }
        val request = OneTimeWorkRequestBuilder<NotionSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun queueArchivePageId(pageId: String) {
        preferences.addPendingArchivePageId(pageId)
    }

    suspend fun syncNow(force: Boolean = false): Boolean = mutex.withLock {
        val token = preferences.integrationToken
        val databaseId = preferences.tasksDatabaseId
        if (token.isBlank() || databaseId.isNullOrBlank()) {
            return@withLock true
        }
        if (!force && !preferences.isAutoSyncEnabled) return@withLock true

        if (preferences.schemaVersion < NotionPreferences.CURRENT_SCHEMA_VERSION) {
            val migration = api.connectAndEnsureDatabase(token, databaseId)
            migration.fold(
                onSuccess = { info ->
                    preferences.tasksDatabaseId = info.databaseId
                    preferences.databaseTitle = info.databaseTitle
                    preferences.titlePropertyName = info.titlePropertyName
                    preferences.schemaVersion = NotionPreferences.CURRENT_SCHEMA_VERSION
                },
                onFailure = { error ->
                    preferences.lastSyncError = error.message ?: "Could not update the Mora Notion table."
                    return@withLock false
                }
            )
        }

        var succeeded = true
        for (pageId in preferences.pendingArchivedNotionPageIds) {
            val result = api.archiveTaskPage(token, pageId)
            if (result.isSuccess) preferences.removePendingArchivePageId(pageId)
            else {
                succeeded = false
                preferences.lastSyncError = result.exceptionOrNull()?.message ?: "Could not archive a deleted task in Notion."
            }
        }

        val tasks = taskDao.getUnsyncedTasksOneShot()
        for (task in tasks) {
            if (isNotionSyncFixture(task.title)) continue
            val result = api.upsertTaskPage(token, databaseId, preferences.titlePropertyName, task)
            result.fold(
                onSuccess = { pageId ->
                    taskDao.updateNotionSyncMetadata(task.id, pageId, false, System.currentTimeMillis())
                },
                onFailure = { error ->
                    succeeded = false
                    preferences.lastSyncError = error.message ?: "A task could not be synced to Notion."
                }
            )
        }
        if (succeeded && !pullFromNotionLocked(token, databaseId)) succeeded = false
        if (succeeded) {
            preferences.lastSyncTimestamp = System.currentTimeMillis()
            preferences.lastSyncError = null
        }
        succeeded
    }

    suspend fun pullFromNotion(): Boolean = mutex.withLock {
        val token = preferences.integrationToken
        val databaseId = preferences.tasksDatabaseId
        if (token.isBlank() || databaseId.isNullOrBlank()) return@withLock true
        pullFromNotionLocked(token, databaseId)
    }

    private suspend fun pullFromNotionLocked(token: String, databaseId: String): Boolean {
        val pages = api.queryDatabasePages(token, databaseId, preferences.titlePropertyName).getOrElse {
            preferences.lastSyncError = it.message ?: "Could not pull tasks from Notion."
            return false
        }
        var changed = false
        val pendingArchives = preferences.pendingArchivedNotionPageIds
        val now = System.currentTimeMillis()
        for (remote in pages) {
            if (remote.pageId in pendingArchives || isNotionSyncFixture(remote.title)) continue
            val local = taskDao.getTaskByNotionPageIdOneShot(remote.pageId)
            // Keep local data for remote archives until a reversible tombstone/archive UI exists.
            if (remote.isArchived) continue
            if (local == null) {
                taskDao.insertTask(NotionPayloadMapper.reconcileRemoteTaskWithLocal(remote, null, now))
                changed = true
            } else if (!local.notionSyncDirty) {
                val merged = NotionPayloadMapper.reconcileRemoteTaskWithLocal(remote, local, now)
                if (local.title != merged.title || local.description != merged.description ||
                    local.deadlineTimestamp != merged.deadlineTimestamp || local.status != merged.status ||
                    local.completedAtTimestamp != merged.completedAtTimestamp) {
                    taskDao.updateTask(merged)
                    changed = true
                }
            }
        }
        if (changed) {
            com.example.dueordie.service.OngoingNotificationService.startOrUpdate(appContext)
            com.example.dueordie.widget.DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
        preferences.lastSyncError = null
        return true
    }

    suspend fun connect(token: String, rawTarget: String): Result<NotionDatabaseInfo> {
        val result = api.connectAndEnsureDatabase(token, rawTarget)
        result.onSuccess { info ->
            val parentId = NotionPreferences.extractNotionUuid(rawTarget)
                ?.takeIf { it != info.databaseId }
            preferences.saveConnection(token, rawTarget, info, parentId)
            triggerBackgroundSync()
        }.onFailure { preferences.lastSyncError = it.message }
        return result
    }

    suspend fun syncAllNow(): Boolean = syncNow(force = true)

    companion object {
        const val WORK_NAME = "notion_auto_sync"
    }
}
