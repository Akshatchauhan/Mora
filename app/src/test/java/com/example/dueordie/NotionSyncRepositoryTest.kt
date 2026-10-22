package com.example.dueordie

import com.example.dueordie.data.local.TaskDao
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.notion.NotionSyncTrigger
import com.example.dueordie.data.repository.TaskRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotionSyncRepositoryTest {
    private lateinit var dao: FakeDao
    private lateinit var trigger: FakeTrigger
    private lateinit var repository: TaskRepositoryImpl

    @Before fun setUp() {
        dao = FakeDao()
        trigger = FakeTrigger()
        repository = TaskRepositoryImpl(dao, trigger)
    }

    @Test fun insertMarksDirtyAndTriggersButFixturesAreSkipped() = runBlocking {
        repository.insertTask(task("Write report"))
        assertTrue(dao.tasks.single().notionSyncDirty)
        assertEquals(1, trigger.triggers)
        repository.insertTask(task("[Example] Welcome"))
        assertEquals(1, trigger.triggers)
    }

    @Test fun completionAndUpdateMarkDirtyAndTrigger() = runBlocking {
        val id = repository.insertTask(task("Plan trip"))
        trigger.triggers = 0
        repository.markAsCompleted(id)
        assertTrue(dao.tasks.single().notionSyncDirty)
        assertEquals(1, trigger.triggers)
        repository.updateTask(dao.tasks.single().copy(title = "Plan better trip", notionSyncDirty = false))
        assertTrue(dao.tasks.single().notionSyncDirty)
        assertEquals(2, trigger.triggers)
    }

    @Test fun deleteQueuesNotionArchiveAndTriggers() = runBlocking {
        val existing = task("Remove me").copy(id = 42L, notionPageId = "notion-page")
        dao.tasks += existing
        repository.deleteTask(existing)
        assertEquals(listOf("notion-page"), trigger.archivedIds)
        assertEquals(1, trigger.triggers)
        assertFalse(dao.tasks.any { it.id == 42L })
    }

    @Test fun fixtureMutationsDoNotTriggerOrQueueNotionSync() = runBlocking {
        val fixture = task("Submit_quarterly_report").copy(id = 8L, notionPageId = "fixture-page")
        dao.tasks += fixture
        repository.deleteTask(fixture)
        assertTrue(trigger.archivedIds.isEmpty())
        assertEquals(0, trigger.triggers)
    }

    private fun task(title: String) = Task(title = title, deadlineTimestamp = System.currentTimeMillis() + 60_000)

    private class FakeTrigger : NotionSyncTrigger {
        var triggers = 0
        val archivedIds = mutableListOf<String>()
        override fun triggerBackgroundSync() { triggers++ }
        override fun queueArchivePageId(pageId: String) { archivedIds += pageId }
    }

    private class FakeDao : TaskDao {
        val tasks = mutableListOf<Task>()
        override fun getAllTasks(): Flow<List<Task>> = flowOf(tasks)
        override fun getPendingTasks(): Flow<List<Task>> = flowOf(tasks.filter { it.status == TaskStatus.PENDING })
        override fun getCompletedTasks(): Flow<List<Task>> = flowOf(tasks.filter { it.status == TaskStatus.COMPLETED })
        override fun getOverdueTasks(): Flow<List<Task>> = flowOf(tasks.filter { it.status == TaskStatus.OVERDUE })
        override fun getTaskById(id: Long): Flow<Task?> = flowOf(tasks.firstOrNull { it.id == id })
        override suspend fun getTaskByIdOneShot(id: Long): Task? = tasks.firstOrNull { it.id == id }
        override suspend fun getTaskByNotionPageIdOneShot(notionPageId: String): Task? = tasks.firstOrNull { it.notionPageId == notionPageId }
        override suspend fun getPendingTasksOneShot(): List<Task> = tasks.filter { it.status == TaskStatus.PENDING }
        override suspend fun getPendingTasksDueBefore(targetTimestamp: Long): List<Task> = tasks.filter { it.status == TaskStatus.PENDING && it.deadlineTimestamp <= targetTimestamp }
        override suspend fun insertTask(task: Task): Long {
            val id = if (task.id > 0) task.id else (tasks.maxOfOrNull { it.id } ?: 0L) + 1L
            tasks += task.copy(id = id)
            return id
        }
        override suspend fun updateTask(task: Task) {
            val index = tasks.indexOfFirst { it.id == task.id }
            if (index >= 0) tasks[index] = task
        }
        override suspend fun deleteTask(task: Task) { tasks.removeAll { it.id == task.id } }
        override suspend fun deleteTaskById(id: Long) { tasks.removeAll { it.id == id } }
        override suspend fun updateTaskStatus(id: Long, status: TaskStatus, completedAt: Long?) {
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) tasks[index] = tasks[index].copy(status = status, completedAtTimestamp = completedAt, notionSyncDirty = true)
        }
        override suspend fun markOverdueTasks(currentTimeMs: Long): Int {
            var changed = 0
            for (i in tasks.indices) if (tasks[i].status == TaskStatus.PENDING && tasks[i].deadlineTimestamp <= currentTimeMs) {
                tasks[i] = tasks[i].copy(status = TaskStatus.OVERDUE, notionSyncDirty = true)
                changed++
            }
            return changed
        }
        override suspend fun markTodayPlanTasksDirty(dayStartMs: Long, nextDayStartMs: Long): Int {
            var changed = 0
            for (i in tasks.indices) if (tasks[i].status == TaskStatus.PENDING && tasks[i].notionPageId != null &&
                !tasks[i].notionSyncDirty && tasks[i].deadlineTimestamp >= dayStartMs && tasks[i].deadlineTimestamp < nextDayStartMs) {
                tasks[i] = tasks[i].copy(notionSyncDirty = true)
                changed++
            }
            return changed
        }
        override suspend fun getAllTasksOneShot(): List<Task> = tasks.toList()
        override suspend fun getUnsyncedTasksOneShot(): List<Task> = tasks.filter { it.notionSyncDirty || it.notionPageId == null }
        override suspend fun updateNotionSyncMetadata(id: Long, notionPageId: String?, dirty: Boolean, syncedAt: Long?) {
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) tasks[index] = tasks[index].copy(notionPageId = notionPageId, notionSyncDirty = dirty, lastSyncedAtTimestamp = syncedAt)
        }
    }
}
