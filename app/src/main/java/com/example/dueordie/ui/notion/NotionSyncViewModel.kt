package com.example.dueordie.ui.notion

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dueordie.DueOrDieApplication
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.notion.NotionSyncManager
import com.example.dueordie.data.notion.isNotionSyncFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class NotionSyncUiState(
    val token: String = "",
    val target: String = "",
    val isConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val autoSync: Boolean = true,
    val databaseTitle: String? = null,
    val status: String = "Not connected",
    val error: String? = null,
    val syncedCount: Int = 0,
    val pendingCount: Int = 0,
    val lastSyncTimestamp: Long? = null
)

class NotionSyncViewModel(
    private val manager: NotionSyncManager,
    private val context: Context
) : ViewModel() {
    private val prefs = manager.preferences
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<NotionSyncUiState> = _uiState.asStateFlow()
    private val preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener

    init {
        preferenceListener = prefs.registerListener { refreshConnectionState() }
        viewModelScope.launch {
            (context.applicationContext as DueOrDieApplication).database.taskDao().getAllTasks().collectLatest { tasks ->
                publishStats(tasks)
            }
        }
    }

    private fun initialState() = NotionSyncUiState(
        token = prefs.integrationToken,
        target = prefs.rawTargetInput,
        isConnected = !prefs.tasksDatabaseId.isNullOrBlank(),
        autoSync = prefs.isAutoSyncEnabled,
        databaseTitle = prefs.databaseTitle,
        status = statusText(prefs.pendingArchivedNotionPageIds.size),
        error = prefs.lastSyncError,
        lastSyncTimestamp = prefs.lastSyncTimestamp,
        pendingCount = prefs.pendingArchivedNotionPageIds.size
    )

    fun setToken(value: String) { _uiState.value = _uiState.value.copy(token = value) }
    fun setTarget(value: String) { _uiState.value = _uiState.value.copy(target = value) }

    fun setAutoSync(enabled: Boolean) {
        prefs.isAutoSyncEnabled = enabled
        _uiState.value = _uiState.value.copy(autoSync = enabled)
        if (enabled) manager.triggerBackgroundSync()
    }

    fun connect() {
        val current = _uiState.value
        if (current.token.isBlank()) {
            _uiState.value = current.copy(error = "Enter your Notion integration token. A page link is optional if the integration can access a page.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null, status = "Connecting…")
            val result = manager.connect(current.token, current.target)
            if (result.isSuccess) {
                val success = manager.syncAllNow()
                refreshConnectionState()
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    status = if (success) "Connected" else statusText(_uiState.value.pendingCount),
                    error = if (success) null else prefs.lastSyncError
                )
            } else {
                val message = result.exceptionOrNull()?.message ?: "Could not connect to Notion."
                prefs.lastSyncError = message
                _uiState.value = _uiState.value.copy(isSyncing = false, status = "Connection issue", error = message)
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, status = "Syncing…", error = null)
            val success = manager.syncAllNow()
            refreshConnectionState()
            _uiState.value = _uiState.value.copy(isSyncing = false, error = if (success) null else prefs.lastSyncError)
        }
    }

    fun disconnect() {
        prefs.disconnect()
        _uiState.value = NotionSyncUiState()
    }

    private fun publishStats(tasks: List<Task>) {
        val syncable = tasks.filterNot { isNotionSyncFixture(it.title) }
        _uiState.value = _uiState.value.copy(
            syncedCount = syncable.count { it.notionPageId != null && !it.notionSyncDirty },
            pendingCount = syncable.count { it.notionSyncDirty || it.notionPageId == null } + prefs.pendingArchivedNotionPageIds.size
        )
        refreshConnectionState()
    }

    private fun refreshConnectionState() {
        val connected = !prefs.tasksDatabaseId.isNullOrBlank()
        _uiState.value = _uiState.value.copy(
            token = _uiState.value.token.ifBlank { prefs.integrationToken },
            target = _uiState.value.target.ifBlank { prefs.rawTargetInput },
            isConnected = connected,
            autoSync = prefs.isAutoSyncEnabled,
            databaseTitle = prefs.databaseTitle,
            status = if (_uiState.value.isSyncing) _uiState.value.status else statusText(_uiState.value.pendingCount),
            error = prefs.lastSyncError,
            lastSyncTimestamp = prefs.lastSyncTimestamp
        )
    }

    private fun statusText(pendingCount: Int): String {
        if (prefs.tasksDatabaseId.isNullOrBlank()) return "Not connected"
        if (!isOnline() && pendingCount > 0) return "Offline, queued"
        return if (prefs.lastSyncError == null) "Connected" else "Needs attention"
    }

    private fun isOnline(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override fun onCleared() {
        prefs.unregisterListener(preferenceListener)
        super.onCleared()
    }

    companion object {
        fun Factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as DueOrDieApplication
                return NotionSyncViewModel(app.notionSyncManager, context.applicationContext) as T
            }
        }
    }
}
