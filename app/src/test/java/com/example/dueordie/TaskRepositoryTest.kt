package com.example.dueordie

import com.example.dueordie.data.local.TaskDao
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.UrgencyLevel
import com.example.dueordie.data.repository.TaskRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TaskRepositoryTest {

    private lateinit var fakeDao: FakeTaskDao
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeTaskDao()
        repository = TaskRepositoryImpl(fakeDao)
    }

    @Test
    fun testCalculateUrgencySummary() {
        val now = System.currentTimeMillis()
        val tasks = listOf(
            Task(id = 1, title = "T1", deadlineTimestamp = now - 1000, status = TaskStatus.PENDING), // Overdue
            Task(id = 2, title = "T2", deadlineTimestamp = now + (1 * 3600 * 1000), status = TaskStatus.PENDING), // Critical (<3h)
            Task(id = 3, title = "T3", deadlineTimestamp = now + (6 * 3600 * 1000), status = TaskStatus.PENDING), // High (<12h)
            Task(id = 4, title = "T4", deadlineTimestamp = now + (18 * 3600 * 1000), status = TaskStatus.PENDING), // Medium (<24h)
            Task(id = 5, title = "T5", deadlineTimestamp = now + (48 * 3600 * 1000), status = TaskStatus.PENDING), // Low (>24h)
            Task(id = 6, title = "T6", deadlineTimestamp = now - 10000, status = TaskStatus.COMPLETED) // Ignored in pending count
        )

        val summary = repository.calculateUrgencySummary(tasks, now)
        assertEquals(5, summary.totalPending)
        assertEquals(1, summary.overdueCount)
        assertEquals(1, summary.criticalCount)
        assertEquals(1, summary.highCount)
        assertEquals(1, summary.mediumCount)
        assertEquals(1, summary.lowCount)
        assertEquals(UrgencyLevel.OVERDUE, summary.highestUrgency)
    }

    @Test
    fun testCheckAndUpdateOverdueTasks() = runBlocking {
        val now = System.currentTimeMillis()
        fakeDao.tasks.add(Task(id = 1, title = "Overdue 1", deadlineTimestamp = now - 1000, status = TaskStatus.PENDING))
        fakeDao.tasks.add(Task(id = 2, title = "Valid", deadlineTimestamp = now + 100000, status = TaskStatus.PENDING))

        val count = repository.checkAndUpdateOverdueTasks(now)
        assertEquals(1, count)
        assertEquals(TaskStatus.OVERDUE, fakeDao.tasks.first { it.id == 1L }.status)
        assertEquals(TaskStatus.PENDING, fakeDao.tasks.first { it.id == 2L }.status)
    }

    private class FakeTaskDao : TaskDao {
        val tasks = mutableListOf<Task>()

        override fun getAllTasks(): Flow<List<Task>> = flowOf(tasks)
        override fun getPendingTasks(): Flow<List<Task>> = flowOf(tasks.filter { it.status == TaskStatus.PENDING })
        override fun getCompletedTasks(): Flow<List<Task>> = flowOf(tasks.filter { it.status == TaskStatus.COMPLETED })
        override fun getOverdueTasks(): Flow<List<Task>> = flowOf(tasks.filter { it.status == TaskStatus.OVERDUE })
        override fun getTaskById(id: Long): Flow<Task?> = flowOf(tasks.find { it.id == id })
        override suspend fun getTaskByIdOneShot(id: Long): Task? = tasks.find { it.id == id }
        override suspend fun getTaskByNotionPageIdOneShot(notionPageId: String): Task? = tasks.find { it.notionPageId == notionPageId }
        override suspend fun getPendingTasksOneShot(): List<Task> = tasks.filter { it.status == TaskStatus.PENDING }
        override suspend fun getPendingTasksDueBefore(targetTimestamp: Long): List<Task> {
            return tasks.filter { it.status == TaskStatus.PENDING && it.deadlineTimestamp <= targetTimestamp }
        }

        override suspend fun insertTask(task: Task): Long {
            val newId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
            val newTask = task.copy(id = newId)
            tasks.add(newTask)
            return newId
        }

        override suspend fun updateTask(task: Task) {
            val index = tasks.indexOfFirst { it.id == task.id }
            if (index >= 0) tasks[index] = task
        }

        override suspend fun deleteTask(task: Task) {
            tasks.removeAll { it.id == task.id }
        }

        override suspend fun deleteTaskById(id: Long) {
            tasks.removeAll { it.id == id }
        }

        override suspend fun updateTaskStatus(id: Long, status: TaskStatus, completedAt: Long?) {
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) {
                tasks[index] = tasks[index].copy(status = status, completedAtTimestamp = completedAt)
            }
        }

        override suspend fun markOverdueTasks(currentTimeMs: Long): Int {
            var count = 0
            for (i in tasks.indices) {
                if (tasks[i].status == TaskStatus.PENDING && tasks[i].deadlineTimestamp < currentTimeMs) {
                    tasks[i] = tasks[i].copy(status = TaskStatus.OVERDUE)
                    count++
                }
            }
            return count
        }

        override suspend fun markTodayPlanTasksDirty(dayStartMs: Long, nextDayStartMs: Long): Int = 0

        override suspend fun getAllTasksOneShot(): List<Task> = tasks.sortedBy { it.createdAtTimestamp }
        override suspend fun getUnsyncedTasksOneShot(): List<Task> = tasks.filter { it.notionSyncDirty || it.notionPageId == null }
        override suspend fun updateNotionSyncMetadata(id: Long, notionPageId: String?, dirty: Boolean, syncedAt: Long?) {
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) tasks[index] = tasks[index].copy(notionPageId = notionPageId, notionSyncDirty = dirty, lastSyncedAtTimestamp = syncedAt)
        }
    }
}
