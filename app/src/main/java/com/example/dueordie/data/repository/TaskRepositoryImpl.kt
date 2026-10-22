package com.example.dueordie.data.repository

import com.example.dueordie.data.local.TaskDao
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.UrgencyLevel
import com.example.dueordie.data.model.UrgencySummary
import com.example.dueordie.data.notion.NotionSyncTrigger
import com.example.dueordie.data.notion.isNotionSyncFixture
import kotlinx.coroutines.flow.Flow

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val notionSyncManager: NotionSyncTrigger? = null
) : TaskRepository {

    override val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    override val pendingTasks: Flow<List<Task>> = taskDao.getPendingTasks()

    override val completedTasks: Flow<List<Task>> = taskDao.getCompletedTasks()

    override val overdueTasks: Flow<List<Task>> = taskDao.getOverdueTasks()

    override fun getTaskById(id: Long): Flow<Task?> = taskDao.getTaskById(id)

    override suspend fun getTaskByIdOneShot(id: Long): Task? = taskDao.getTaskByIdOneShot(id)

    override suspend fun insertTask(task: Task): Long {
        val id = taskDao.insertTask(task.copy(notionSyncDirty = true))
        triggerIfSyncable(task.title)
        return id
    }

    override suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.copy(notionSyncDirty = true))
        triggerIfSyncable(task.title)
    }

    override suspend fun deleteTask(task: Task) {
        if (!isNotionSyncFixture(task.title)) task.notionPageId?.let { notionSyncManager?.queueArchivePageId(it) }
        taskDao.deleteTask(task)
        triggerIfSyncable(task.title)
    }

    override suspend fun deleteTaskById(id: Long) {
        val task = taskDao.getTaskByIdOneShot(id)
        if (task != null && !isNotionSyncFixture(task.title)) task.notionPageId?.let { notionSyncManager?.queueArchivePageId(it) }
        taskDao.deleteTaskById(id)
        task?.let { triggerIfSyncable(it.title) }
    }

    override suspend fun markAsCompleted(id: Long) {
        val task = taskDao.getTaskByIdOneShot(id)
        taskDao.updateTaskStatus(
            id = id,
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        task?.let { triggerIfSyncable(it.title) }
    }

    override suspend fun markAsPending(id: Long) {
        val task = taskDao.getTaskByIdOneShot(id)
        taskDao.updateTaskStatus(
            id = id,
            status = TaskStatus.PENDING,
            completedAt = null
        )
        task?.let { triggerIfSyncable(it.title) }
    }

    override suspend fun markAsOverdue(id: Long) {
        val task = taskDao.getTaskByIdOneShot(id)
        taskDao.updateTaskStatus(
            id = id,
            status = TaskStatus.OVERDUE,
            completedAt = null
        )
        task?.let { triggerIfSyncable(it.title) }
    }

    override suspend fun checkAndUpdateOverdueTasks(currentTimeMs: Long): Int {
        val count = taskDao.markOverdueTasks(currentTimeMs)
        if (count > 0) notionSyncManager?.triggerBackgroundSync()
        return count
    }

    private fun triggerIfSyncable(title: String) {
        if (!isNotionSyncFixture(title)) notionSyncManager?.triggerBackgroundSync()
    }

    override suspend fun getPendingTasksDueBefore(targetTimestamp: Long): List<Task> {
        return taskDao.getPendingTasksDueBefore(targetTimestamp)
    }

    override fun calculateUrgencySummary(tasks: List<Task>, nowMs: Long): UrgencySummary {
        var pending = 0
        var overdue = 0
        var critical = 0
        var high = 0
        var medium = 0
        var low = 0

        for (task in tasks) {
            if (task.status == TaskStatus.COMPLETED) continue
            pending++
            when (task.calculateUrgencyLevel(nowMs)) {
                UrgencyLevel.OVERDUE -> overdue++
                UrgencyLevel.CRITICAL -> critical++
                UrgencyLevel.HIGH -> high++
                UrgencyLevel.MEDIUM -> medium++
                UrgencyLevel.LOW -> low++
            }
        }

        return UrgencySummary(
            totalPending = pending,
            overdueCount = overdue,
            criticalCount = critical,
            highCount = high,
            mediumCount = medium,
            lowCount = low
        )
    }
}
