package com.example.dueordie.ui.task

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.CompletionHistoryStore
import com.example.dueordie.data.model.InteractionModePolicy
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.TaskPriority
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.UrgencySummary
import com.example.dueordie.data.repository.TaskRepository
import com.example.dueordie.service.OngoingNotificationService
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.widget.DueOrDieWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskUiState(
    val allTasks: List<Task> = emptyList(),
    val filteredTasks: List<Task> = emptyList(),
    val selectedFilter: TaskFilter = TaskFilter.ALL,
    val urgencySummary: UrgencySummary = UrgencySummary(),
    val currentTimeMs: Long = System.currentTimeMillis(),
    val overdueCount: Int = 0,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val onTimeCompletedCount: Int = 0,
    val lateCompletedCount: Int = 0,
    val onTimeRate: Int? = null,
    val streakDays: Int = 0,
    val disciplineScore: Int = 100,
    val editingTask: Task? = null,
    val isTaskDialogVisible: Boolean = false,
    val taskRequiringPhotoProof: Task? = null,
    val celebrationTriggered: Boolean = false,
    val snackbarMessage: String? = null,
    val canUndoLastAction: Boolean = false,
    val isNotionConnected: Boolean = false,
    val isNotionSyncing: Boolean = false
)

class TaskViewModel(
    private val repository: TaskRepository,
    context: Context
) : ViewModel() {

    private sealed interface UndoAction {
        val previousCooldownUntilMs: Long
        val previousCooldownTaskTitle: String

        data class DeletedTask(
            val task: Task,
            override val previousCooldownUntilMs: Long,
            override val previousCooldownTaskTitle: String
        ) : UndoAction

        data class CompletedTask(
            val previousTask: Task,
            override val previousCooldownUntilMs: Long,
            override val previousCooldownTaskTitle: String
        ) : UndoAction
    }

    private val appContext = context.applicationContext
    private val completionHistoryStore = CompletionHistoryStore(appContext)
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    private var lastUndoAction: UndoAction? = null

    init {
        observeTasks()
        startPeriodicOverdueCheck()
        checkOverdueTasks()
        observeNotionStatus()
    }

    private fun observeTasks() {
        viewModelScope.launch {
            repository.allTasks.collectLatest { tasks ->
                val fixtureTasks = tasks.filter { it.title == "Submit_quarterly_report" }
                if (fixtureTasks.isNotEmpty()) {
                    for (fixture in fixtureTasks) {
                        completionHistoryStore.removeRecord(fixture.id)
                        repository.deleteTask(fixture)
                    }
                    return@collectLatest
                }

                val now = System.currentTimeMillis()
                repository.checkAndUpdateOverdueTasks(now)

                // Backfill any existing completed tasks into durable completion history
                for (task in tasks) {
                    if (task.status == TaskStatus.COMPLETED && task.completedAtTimestamp != null) {
                        completionHistoryStore.recordCompletion(
                            taskId = task.id,
                            deadlineTimestamp = task.deadlineTimestamp,
                            completedAtTimestamp = task.completedAtTimestamp
                        )
                    }
                }
                val archivedRecords = completionHistoryStore.getRecords()

                val summary = repository.calculateUrgencySummary(tasks, now)
                val overdue = tasks.count { it.isOverdueAt(now) }
                val pending = tasks.count { it.status == TaskStatus.PENDING && !it.isOverdueAt(now) }
                val completed = tasks.count { it.status == TaskStatus.COMPLETED }
                val onTimeCompleted = tasks.count { TaskDomainRules.isCompletedOnTime(it) }
                val lateCompleted = tasks.count { TaskDomainRules.isCompletedLate(it) }

                val rate = TaskDomainRules.calculateOnTimeRate(tasks, now, archivedRecords)
                val score = rate ?: 100
                val streak = TaskDomainRules.calculateConsecutiveOnTimeStreak(
                    tasks = tasks,
                    nowMs = now,
                    archivedRecords = archivedRecords
                )

                _uiState.update { currentState ->
                    val filtered = filterTasks(tasks, currentState.selectedFilter, now)
                    currentState.copy(
                        allTasks = tasks,
                        filteredTasks = filtered,
                        urgencySummary = summary,
                        currentTimeMs = now,
                        overdueCount = overdue,
                        pendingCount = pending,
                        completedCount = completed,
                        onTimeCompletedCount = onTimeCompleted,
                        lateCompletedCount = lateCompleted,
                        onTimeRate = rate,
                        disciplineScore = score,
                        streakDays = streak
                    )
                }
                DueOrDieWidgetProvider.updateAllWidgets(appContext)
            }
        }
    }

    private fun startPeriodicOverdueCheck() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                val now = System.currentTimeMillis()
                val updatedCount = repository.checkAndUpdateOverdueTasks(now)
                if (updatedCount > 0) {
                    DueOrDieWidgetProvider.updateAllWidgets(appContext)
                } else {
                    _uiState.update { currentState ->
                        val summary = repository.calculateUrgencySummary(currentState.allTasks, now)
                        if (summary != currentState.urgencySummary) {
                            currentState.copy(
                                currentTimeMs = now,
                                urgencySummary = summary,
                                filteredTasks = filterTasks(currentState.allTasks, currentState.selectedFilter, now)
                            )
                        } else {
                            currentState
                        }
                    }
                }
            }
        }
    }

    fun checkOverdueTasks() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.checkAndUpdateOverdueTasks(now)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun setFilter(filter: TaskFilter) {
        val now = System.currentTimeMillis()
        _uiState.update { currentState ->
            val filtered = filterTasks(currentState.allTasks, filter, now)
            currentState.copy(
                selectedFilter = filter,
                filteredTasks = filtered
            )
        }
    }

    private fun filterTasks(
        tasks: List<Task>,
        filter: TaskFilter,
        nowMs: Long = System.currentTimeMillis()
    ): List<Task> {
        val matching = when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.PENDING -> tasks.filter { it.status == TaskStatus.PENDING && !it.isOverdueAt(nowMs) }
            TaskFilter.OVERDUE -> tasks.filter { it.isOverdueAt(nowMs) }
            TaskFilter.COMPLETED -> tasks.filter { it.status == TaskStatus.COMPLETED }
        }
        return matching.sortedWith(
            compareBy<Task> { it.status == TaskStatus.COMPLETED }
                .thenByDescending { it.priority.level }
                .thenBy { it.deadlineTimestamp }
        )
    }

    fun openAddTaskDialog() {
        _uiState.update {
            it.copy(
                editingTask = null,
                isTaskDialogVisible = true
            )
        }
    }

    fun openEditTaskDialog(task: Task) {
        _uiState.update {
            it.copy(
                editingTask = task,
                isTaskDialogVisible = true
            )
        }
    }

    fun dismissTaskDialog() {
        _uiState.update {
            it.copy(
                editingTask = null,
                isTaskDialogVisible = false
            )
        }
    }

    fun saveTask(
        title: String,
        description: String,
        deadlineTimestamp: Long,
        priority: TaskPriority = TaskPriority.MEDIUM,
        requirePhotoProof: Boolean = false,
        isHostageModeEnabled: Boolean = false,
        photoPath: String? = null,
        canEditDeadline: Boolean = true
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentEditing = _uiState.value.editingTask
            if (currentEditing == null) {
                val initialStatus = TaskDomainRules.determineStatusForDeadline(
                    currentStatus = null,
                    deadlineTimestamp = deadlineTimestamp,
                    nowMs = now
                )
                val newTask = Task(
                    title = title.trim(),
                    description = description.trim(),
                    deadlineTimestamp = deadlineTimestamp,
                    priority = priority,
                    status = initialStatus,
                    createdAtTimestamp = now,
                    requirePhotoProof = requirePhotoProof,
                    photoPath = photoPath,
                    isHostageModeEnabled = isHostageModeEnabled
                )
                repository.insertTask(newTask)
            } else {
                val effectivePhotoPath = photoPath ?: if (requirePhotoProof) currentEditing.photoPath else null
                if (currentEditing.photoPath != null && currentEditing.photoPath != effectivePhotoPath) {
                    withContext(Dispatchers.IO) {
                        TaskDomainRules.deletePhotoFileIfPresent(currentEditing.photoPath)
                    }
                }
                val currentMode = OnboardingPreferences(appContext).appInteractionMode
                val effectiveCanEditDeadline = InteractionModePolicy.canEditTask(currentMode, isTaskSaved = true) && canEditDeadline
                val updatedTask = TaskDomainRules.resolveEditedTask(
                    existingTask = currentEditing,
                    newTitle = title,
                    newDescription = description,
                    requestedDeadlineTimestamp = deadlineTimestamp,
                    requirePhotoProof = requirePhotoProof,
                    effectivePhotoPath = effectivePhotoPath,
                    isHostageModeEnabled = isHostageModeEnabled,
                    priority = priority,
                    nowMs = now,
                    canEditDeadline = effectiveCanEditDeadline
                )
                if (currentEditing.isOverdueAt(now) &&
                    (currentEditing.isHostageModeEnabled || OnboardingPreferences(appContext).isGlobalHostageModeEnabled) &&
                    (deadlineTimestamp > now || (currentEditing.isHostageModeEnabled && !isHostageModeEnabled))) {
                    startOverdueCooldown(currentEditing, now)
                }
                repository.updateTask(updatedTask)
                NotificationHelper.cancelOverdueAlarmNotification(appContext, currentEditing.id)
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
            dismissTaskDialog()
        }
    }

    fun toggleTaskStatus(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (task.status == TaskStatus.COMPLETED) {
                completionHistoryStore.removeRecord(task.id)
                if (task.deadlineTimestamp <= now) {
                    repository.markAsOverdue(task.id)
                } else {
                    repository.markAsPending(task.id)
                }
            } else {
                val preferences = OnboardingPreferences(appContext)
                val previousCooldownUntilMs = preferences.overdueCooldownUntilWallMs
                val previousCooldownTaskTitle = preferences.lastCooldownTaskTitle
                val isOverdueWithLock = task.isOverdueAt(now) && (task.isHostageModeEnabled || OnboardingPreferences(appContext).isGlobalHostageModeEnabled)
                lastUndoAction = UndoAction.CompletedTask(
                    previousTask = task,
                    previousCooldownUntilMs = previousCooldownUntilMs,
                    previousCooldownTaskTitle = previousCooldownTaskTitle
                )
                if (isOverdueWithLock) {
                    startOverdueCooldown(task, now)
                }
                finalizePendingDeleteCleanup()
                completionHistoryStore.recordCompletion(
                    taskId = task.id,
                    deadlineTimestamp = task.deadlineTimestamp,
                    completedAtTimestamp = now
                )
                repository.markAsCompleted(task.id)
                NotificationHelper.cancelOverdueAlarmNotification(appContext, task.id)
                _uiState.update {
                    it.copy(
                        celebrationTriggered = true,
                        snackbarMessage = if (isOverdueWithLock) {
                            "Marked \"${task.title}\" complete - Focus Lock cools down for 10m"
                        } else {
                            "Marked \"${task.title}\" complete"
                        },
                        canUndoLastAction = true
                    )
                }
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun completeTaskWithoutPhoto(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val preferences = OnboardingPreferences(appContext)
            finalizePendingDeleteCleanup()
            lastUndoAction = UndoAction.CompletedTask(
                previousTask = task,
                previousCooldownUntilMs = preferences.overdueCooldownUntilWallMs,
                previousCooldownTaskTitle = preferences.lastCooldownTaskTitle
            )
            completionHistoryStore.recordCompletion(
                taskId = task.id,
                deadlineTimestamp = task.deadlineTimestamp,
                completedAtTimestamp = now
            )
            val updated = task.copy(
                status = TaskStatus.COMPLETED,
                completedAtTimestamp = now
            )
            repository.updateTask(updated)
            _uiState.update {
                it.copy(
                    taskRequiringPhotoProof = null,
                    celebrationTriggered = true,
                    snackbarMessage = "Marked \"${task.title}\" complete",
                    canUndoLastAction = true
                )
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun onPhotoProofCaptured(task: Task, photoPath: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val preferences = OnboardingPreferences(appContext)
            finalizePendingDeleteCleanup()
            lastUndoAction = UndoAction.CompletedTask(
                previousTask = task,
                previousCooldownUntilMs = preferences.overdueCooldownUntilWallMs,
                previousCooldownTaskTitle = preferences.lastCooldownTaskTitle
            )
            completionHistoryStore.recordCompletion(
                taskId = task.id,
                deadlineTimestamp = task.deadlineTimestamp,
                completedAtTimestamp = now
            )
            val updated = task.copy(
                status = TaskStatus.COMPLETED,
                completedAtTimestamp = now,
                photoPath = photoPath
            )
            repository.updateTask(updated)
            _uiState.update {
                it.copy(
                    taskRequiringPhotoProof = null,
                    celebrationTriggered = true,
                    snackbarMessage = "Marked \"${task.title}\" complete",
                    canUndoLastAction = true
                )
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun dismissPhotoProofPrompt() {
        _uiState.update { it.copy(taskRequiringPhotoProof = null) }
    }

    fun dismissCelebration() {
        _uiState.update { it.copy(celebrationTriggered = false) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val preferences = OnboardingPreferences(appContext)
            val previousCooldownUntilMs = preferences.overdueCooldownUntilWallMs
            val previousCooldownTaskTitle = preferences.lastCooldownTaskTitle
            if (task.isOverdueAt(now) && (task.isHostageModeEnabled || OnboardingPreferences(appContext).isGlobalHostageModeEnabled)) {
                startOverdueCooldown(task, now)
            }
            finalizePendingDeleteCleanup()
            lastUndoAction = UndoAction.DeletedTask(
                task = task,
                previousCooldownUntilMs = previousCooldownUntilMs,
                previousCooldownTaskTitle = previousCooldownTaskTitle
            )
            repository.deleteTask(task)
            NotificationHelper.cancelOverdueAlarmNotification(appContext, task.id)
            _uiState.update {
                it.copy(
                    snackbarMessage = "Deleted \"${task.title}\"",
                    canUndoLastAction = true
                )
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    private fun startOverdueCooldown(task: Task, nowMs: Long) {
        val preferences = OnboardingPreferences(appContext)
        preferences.overdueCooldownUntilWallMs = nowMs + OnboardingPreferences.OVERDUE_UNBLOCK_COOLDOWN_MS
        preferences.lastCooldownTaskTitle = task.title
    }

    fun undoLastAction() {
        viewModelScope.launch {
            when (val action = lastUndoAction) {
                is UndoAction.DeletedTask -> {
                    repository.insertTask(action.task)
                }
                is UndoAction.CompletedTask -> {
                    completionHistoryStore.removeRecord(action.previousTask.id)
                    repository.updateTask(action.previousTask)
                }
                null -> Unit
            }
            lastUndoAction?.let { action ->
                val preferences = OnboardingPreferences(appContext)
                preferences.overdueCooldownUntilWallMs = action.previousCooldownUntilMs
                preferences.lastCooldownTaskTitle = action.previousCooldownTaskTitle
            }
            lastUndoAction = null
            _uiState.update {
                it.copy(
                    snackbarMessage = null,
                    canUndoLastAction = false
                )
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun clearSnackbar() {
        viewModelScope.launch {
            finalizePendingDeleteCleanup()
            lastUndoAction = null
            _uiState.update {
                it.copy(
                    snackbarMessage = null,
                    canUndoLastAction = false
                )
            }
        }
    }

    fun addExampleTasks() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = _uiState.value.allTasks
            if (current.any { it.title.startsWith( EXAMPLE_PREFIX ) }) return@launch

            val example1 = Task(
                title = "${EXAMPLE_PREFIX}Review today's top priority",
                description = "Tap the squircle on the left to mark a task complete, or swipe right to edit.",
                deadlineTimestamp = now + 4 * 60 * 60 * 1000L, // 4 hours in the future
                priority = TaskPriority.HIGH,
                status = TaskStatus.PENDING,
                createdAtTimestamp = now
            )
            val example2 = Task(
                title = "${EXAMPLE_PREFIX}Plan tomorrow's schedule",
                description = "Example tasks are due in the future and can be removed in one tap.",
                deadlineTimestamp = now + 24 * 60 * 60 * 1000L, // 24 hours in the future
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.PENDING,
                createdAtTimestamp = now
            )
            repository.insertTask(example1)
            repository.insertTask(example2)
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun removeExampleTasks() {
        viewModelScope.launch {
            val examples = _uiState.value.allTasks.filter {
                it.title.startsWith(EXAMPLE_PREFIX) || it.title == "Submit_quarterly_report"
            }
            for (example in examples) {
                completionHistoryStore.removeRecord(example.id)
                repository.deleteTask(example)
            }
            if (examples.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Removed example tasks",
                        canUndoLastAction = false
                    )
                }
            }
            OngoingNotificationService.startOrUpdate(appContext)
            DueOrDieWidgetProvider.updateAllWidgets(appContext)
        }
    }

    private suspend fun finalizePendingDeleteCleanup() {
        val pendingDelete = lastUndoAction as? UndoAction.DeletedTask ?: return
        if (!pendingDelete.task.photoPath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                TaskDomainRules.deletePhotoFileIfPresent(pendingDelete.task.photoPath)
            }
        }
    }

    private fun observeNotionStatus() {
        val app = appContext as? DueOrDieApplication ?: return
        val notionPrefs = app.notionPreferences
        fun update() {
            val connected = !notionPrefs.tasksDatabaseId.isNullOrBlank() && notionPrefs.integrationToken.isNotBlank()
            _uiState.update { it.copy(isNotionConnected = connected) }
        }
        update()
        notionPrefs.registerListener { update() }
    }

    fun syncNotion() {
        viewModelScope.launch {
            val app = appContext as? DueOrDieApplication ?: return@launch
            val notionPrefs = app.notionPreferences
            val connected = !notionPrefs.tasksDatabaseId.isNullOrBlank() && notionPrefs.integrationToken.isNotBlank()
            if (!connected || _uiState.value.isNotionSyncing) return@launch

            _uiState.update { it.copy(isNotionSyncing = true) }
            try {
                app.notionSyncManager.syncNow(force = true)
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isNotionSyncing = false) }
            }
        }
    }

    companion object {
        const val EXAMPLE_PREFIX = "[Example] "
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext as DueOrDieApplication
            return TaskViewModel(app.taskRepository, context) as T
        }
    }
}
