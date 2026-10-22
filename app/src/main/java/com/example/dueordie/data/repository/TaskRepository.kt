package com.example.dueordie.data.repository

import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.UrgencySummary
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    val allTasks: Flow<List<Task>>
    val pendingTasks: Flow<List<Task>>
    val completedTasks: Flow<List<Task>>
    val overdueTasks: Flow<List<Task>>

    fun getTaskById(id: Long): Flow<Task?>
    suspend fun getTaskByIdOneShot(id: Long): Task?
    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(task: Task)
    suspend fun deleteTaskById(id: Long)
    suspend fun markAsCompleted(id: Long)
    suspend fun markAsPending(id: Long)
    suspend fun markAsOverdue(id: Long)
    suspend fun checkAndUpdateOverdueTasks(currentTimeMs: Long = System.currentTimeMillis()): Int
    suspend fun getPendingTasksDueBefore(targetTimestamp: Long): List<Task>
    fun calculateUrgencySummary(tasks: List<Task>, nowMs: Long = System.currentTimeMillis()): UrgencySummary
}
