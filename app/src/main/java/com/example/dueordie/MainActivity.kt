package com.example.dueordie

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.dueordie.service.OngoingNotificationService
import com.example.dueordie.ui.navigation.AppNavigation
import com.example.dueordie.ui.theme.DueOrDieTheme
import com.example.dueordie.util.AppThemeMode
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNotionPullAt = 0L
    private val notionPull = Runnable {
        val app = application as? DueOrDieApplication ?: return@Runnable
        lastNotionPullAt = System.currentTimeMillis()
        lifecycleScope.launch { withContext(Dispatchers.IO) { app.notionSyncManager.pullFromNotion() } }
    }

    override fun onStart() {
        super.onStart()
        val app = application as? DueOrDieApplication ?: return
        if (app.notionPreferences.integrationToken.isBlank() || app.notionPreferences.tasksDatabaseId.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastNotionPullAt < 10_000L) return
        mainHandler.removeCallbacks(notionPull)
        mainHandler.postDelayed(notionPull, 10_000L)
    }

    override fun onResume() {
        super.onResume()
        val onboardingPrefs = OnboardingPreferences(this)
        if (onboardingPrefs.isMindfulPauseEnabled || onboardingPrefs.isGlobalHostageModeEnabled) {
            OngoingNotificationService.startOrUpdate(this)
        }
    }

    override fun onStop() {
        mainHandler.removeCallbacks(notionPull)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val onboardingPrefs = OnboardingPreferences(this)

        // Create Notification Channels
        NotificationHelper.createNotificationChannels(this)

        // Start ongoing notification service if active tasks exist
        OngoingNotificationService.startOrUpdate(this)

        setContent {
            val themeMode by OnboardingPreferences.themeModeFlow.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.AMOLED -> true
            }
            DueOrDieTheme(darkTheme = isDark, amoledBlack = themeMode == AppThemeMode.AMOLED) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(onboardingPrefs = onboardingPrefs)
                }
            }
        }
    }
}
