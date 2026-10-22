package com.example.dueordie.ui.permissions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PermissionsUiState(
    val isNotificationPermissionGranted: Boolean = false,
    val isUsageStatsPermissionGranted: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val isOnboardingCompleted: Boolean = false
) {
    val areAllPermissionsGranted: Boolean
        get() = isNotificationPermissionGranted && isUsageStatsPermissionGranted
}

class PermissionsViewModel(context: Context) : ViewModel() {

    private val onboardingPrefs = OnboardingPreferences(context.applicationContext)
    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        checkPermissions(context)
    }

    fun checkPermissions(context: Context) {
        val notifGranted = PermissionHelper.isNotificationPermissionGranted(context)
        val usageGranted = PermissionHelper.isUsageStatsPermissionGranted(context)
        val accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
        val completed = onboardingPrefs.isOnboardingCompleted

        _uiState.update {
            it.copy(
                isNotificationPermissionGranted = notifGranted,
                isUsageStatsPermissionGranted = usageGranted,
                isAccessibilityServiceEnabled = accessibilityEnabled,
                isOnboardingCompleted = completed
            )
        }
    }

    fun markOnboardingCompleted(context: Context) {
        onboardingPrefs.isOnboardingCompleted = true
        _uiState.update {
            it.copy(isOnboardingCompleted = true)
        }
    }

    fun openUsageAccessSettings(context: Context) {
        PermissionHelper.openUsageAccessSettings(context)
    }

    fun openAccessibilitySettings(context: Context) {
        PermissionHelper.openAccessibilitySettings(context)
    }

    fun openNotificationSettings(context: Context) {
        PermissionHelper.openNotificationSettings(context)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PermissionsViewModel(context) as T
        }
    }
}
