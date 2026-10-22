package com.example.dueordie.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY deadlineTimestamp ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY deadlineTimestamp ASC")
    fun getPendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completedAtTimestamp DESC, deadlineTimestamp ASC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'OVERDUE' ORDER BY deadlineTimestamp DESC")
    fun getOverdueTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskById(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskByIdOneShot(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE notionPageId = :notionPageId LIMIT 1")
    suspend fun getTaskByNotionPageIdOneShot(notionPageId: String): Task?

    @Query("SELECT * FROM tasks WHERE status = 'PENDING'")
    suspend fun getPendingTasksOneShot(): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND deadlineTimestamp <= :targetTimestamp ORDER BY deadlineTimestamp ASC")
    suspend fun getPendingTasksDueBefore(targetTimestamp: Long): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE tasks SET status = :status, completedAtTimestamp = :completedAt, notionSyncDirty = 1 WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: TaskStatus, completedAt: Long?)

    @Query("UPDATE tasks SET status = 'OVERDUE', notionSyncDirty = 1 WHERE status = 'PENDING' AND deadlineTimestamp <= :currentTimeMs")
    suspend fun markOverdueTasks(currentTimeMs: Long): Int

    @Query("UPDATE tasks SET notionSyncDirty = 1 WHERE status = 'PENDING' AND notionPageId IS NOT NULL AND notionSyncDirty = 0 AND deadlineTimestamp >= :dayStartMs AND deadlineTimestamp < :nextDayStartMs")
    suspend fun markTodayPlanTasksDirty(dayStartMs: Long, nextDayStartMs: Long): Int

    @Query("SELECT * FROM tasks ORDER BY createdAtTimestamp ASC")
    suspend fun getAllTasksOneShot(): List<Task>

    @Query("SELECT * FROM tasks WHERE notionSyncDirty = 1 OR notionPageId IS NULL ORDER BY createdAtTimestamp ASC")
    suspend fun getUnsyncedTasksOneShot(): List<Task>

    @Query("UPDATE tasks SET notionPageId = :notionPageId, notionSyncDirty = :dirty, lastSyncedAtTimestamp = :syncedAt WHERE id = :id")
    suspend fun updateNotionSyncMetadata(id: Long, notionPageId: String?, dirty: Boolean, syncedAt: Long?)
}
