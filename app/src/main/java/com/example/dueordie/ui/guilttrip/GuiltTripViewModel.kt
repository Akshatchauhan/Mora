package com.example.dueordie.ui.guilttrip

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.CompletionHistoryStore
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskDomainRules
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.repository.TaskRepository
import com.example.dueordie.util.AppUsageInfo
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import com.example.dueordie.util.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GuiltTripUiState(
    val overdueTasks: List<Task> = emptyList(),
    val topUsedApps: List<AppUsageInfo> = emptyList(),
    val totalScreenTimeMs: Long = 0L,
    val guiltTripMessage: String = "",
    val isUsagePermissionGranted: Boolean = false,
    val isUsageTrackingEnabled: Boolean = false,
    val totalOverdueCount: Int = 0,
    val pendingCount: Int = 0,
    val onTimeCompletedCount: Int = 0,
    val lateCompletedCount: Int = 0,
    val onTimeRate: Int? = null,
    val streakDays: Int = 0,
    val weeklyActivity: List<com.example.dueordie.data.model.DayActivity> = emptyList()
)

class GuiltTripViewModel(
    private val repository: TaskRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val onboardingPrefs = OnboardingPreferences(appContext)
    private val completionHistoryStore = CompletionHistoryStore(appContext)
    private val _uiState = MutableStateFlow(GuiltTripUiState())
    val uiState: StateFlow<GuiltTripUiState> = _uiState.asStateFlow()

    init {
        observeTasks()
        refreshGuiltTripData(appContext)
    }

    private fun observeTasks() {
        viewModelScope.launch {
            repository.allTasks.collectLatest { tasks ->
                val now = System.currentTimeMillis()
                repository.checkAndUpdateOverdueTasks(now)

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
                val activeCompletedIds = tasks.filter { it.status == TaskStatus.COMPLETED }.map { it.id }.toSet()
                val extraArchived = archivedRecords.filter { it.taskId !in activeCompletedIds }

                val overdueList = tasks.filter { it.isOverdueAt(now) }
                val pendingCount = tasks.count { it.status == TaskStatus.PENDING && !it.isOverdueAt(now) }
                val onTimeCount = tasks.count { TaskDomainRules.isCompletedOnTime(it) } +
                    extraArchived.count { it.isCompletedOnTime }
                val lateCount = tasks.count { TaskDomainRules.isCompletedLate(it) } +
                    extraArchived.count { !it.isCompletedOnTime }

                val onTimeRate = TaskDomainRules.calculateOnTimeRate(tasks, now, archivedRecords)
                val streakDays = TaskDomainRules.calculateConsecutiveOnTimeStreak(
                    tasks = tasks,
                    nowMs = now,
                    archivedRecords = archivedRecords
                )
                val weeklyActivity = TaskDomainRules.calculateWeeklyCompletionActivity(
                    tasks = tasks,
                    nowMs = now,
                    archivedRecords = archivedRecords
                )

                _uiState.update {
                    it.copy(
                        overdueTasks = overdueList,
                        totalOverdueCount = overdueList.size,
                        pendingCount = pendingCount,
                        onTimeCompletedCount = onTimeCount,
                        lateCompletedCount = lateCount,
                        onTimeRate = onTimeRate,
                        streakDays = streakDays,
                        weeklyActivity = weeklyActivity
                    )
                }
            }
        }
    }

    fun refreshGuiltTripData(context: Context) {
        viewModelScope.launch {
            val usageGranted = PermissionHelper.isUsageStatsPermissionGranted(context)
            val trackingEnabled = onboardingPrefs.isUsageTrackingEnabled
            val topApps = if (usageGranted && trackingEnabled) {
                withContext(Dispatchers.IO) {
                    UsageStatsHelper.getTopUsedAppsToday(context, limit = 8)
                }
            } else {
                emptyList()
            }
            val totalTime = topApps.sumOf { it.usageTimeMs }
            val currentOverdueCount = _uiState.value.totalOverdueCount
            val message = UsageStatsHelper.buildNeutralScreenTimeSummary(topApps, currentOverdueCount)

            _uiState.update {
                it.copy(
                    topUsedApps = topApps,
                    totalScreenTimeMs = totalTime,
                    guiltTripMessage = message,
                    isUsagePermissionGranted = usageGranted,
                    isUsageTrackingEnabled = trackingEnabled
                )
            }
        }
    }

    fun setUsageTrackingEnabled(context: Context, enabled: Boolean) {
        onboardingPrefs.isUsageTrackingEnabled = enabled
        refreshGuiltTripData(context)
    }

    fun updateAppPauseCycles(packageName: String, cycles: Int) {
        onboardingPrefs.setPauseCyclesForPackage(packageName, cycles)
        refreshGuiltTripData(appContext)
    }

    fun updateAppDailyLimit(packageName: String, limitMinutes: Int) {
        onboardingPrefs.setAppDailyLimitMinutes(packageName, limitMinutes)
        refreshGuiltTripData(appContext)
    }

    fun toggleAppPause(packageName: String, enabled: Boolean) {
        onboardingPrefs.setAppPauseEnabled(packageName, enabled)
        refreshGuiltTripData(appContext)
    }

    fun markTaskCompleted(taskId: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val task = repository.getTaskByIdOneShot(taskId)
            if (task != null) {
                completionHistoryStore.recordCompletion(
                    taskId = task.id,
                    deadlineTimestamp = task.deadlineTimestamp,
                    completedAtTimestamp = now
                )
            }
            repository.markAsCompleted(taskId)
        }
    }

    fun openUsageAccessSettings(context: Context) {
        PermissionHelper.openUsageAccessSettings(context)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext as DueOrDieApplication
            return GuiltTripViewModel(app.taskRepository, context) as T
        }
    }
}
