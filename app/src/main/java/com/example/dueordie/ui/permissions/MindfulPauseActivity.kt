package com.example.dueordie.ui.permissions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.dueordie.MainActivity
import com.example.dueordie.service.OngoingNotificationService
import com.example.dueordie.ui.theme.DueOrDieTheme
import com.example.dueordie.util.AppInteractionMode
import com.example.dueordie.util.AppThemeMode
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.UsageStatsHelper

class MindfulPauseActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (targetPackage.isNotBlank()) {
            OngoingNotificationService.notifyPauseActivityVisible(targetPackage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 10)
        val promptText = intent.getStringExtra(EXTRA_PROMPT_TEXT) ?: ""

        val prefs = OnboardingPreferences(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleStepAway()
            }
        })

        // Check daily usage cap for chosen apps
        val appUsageMs = UsageStatsHelper.getAppUsageTodayMs(this, targetPackage)
        val appMinutes = appUsageMs / 60_000L

        val chosenPackages = prefs.pauseSelectedPackages
        val totalChosenMinutes = chosenPackages.sumOf { pkg ->
            UsageStatsHelper.getAppUsageTodayMs(this, pkg) / 60_000L
        }.toInt()
        val limitMinutes = prefs.dailyChosenAppsLimitMinutes
        val isStrictDailyLimitReached = (prefs.appInteractionMode == AppInteractionMode.STRICT) && (totalChosenMinutes >= limitMinutes)

        setContent {
            val themeMode by OnboardingPreferences.themeModeFlow.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK, AppThemeMode.AMOLED -> true
            }

            DueOrDieTheme(darkTheme = isDark, amoledBlack = themeMode == AppThemeMode.AMOLED) {
                DynamicMindfulPauseContent(
                    targetPackage = targetPackage,
                    promptText = promptText,
                    durationSeconds = durationSeconds,
                    isStrictDailyLimitReached = isStrictDailyLimitReached,
                    dailyScreenTimeMinutes = appMinutes,
                    dailyScreenTimeLimitMinutes = limitMinutes,
                    onContinueToApp = {
                        OngoingNotificationService.notifyContinueClicked(this)
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    },
                    onStepAwayToTasks = {
                        handleStepAway()
                    },
                    onDismiss = {
                        handleStepAway()
                    }
                )
            }
        }
    }

    private fun handleStepAway() {
        OngoingNotificationService.notifyLeaveClicked(this)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_target_package"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_PROMPT_TEXT = "extra_prompt_text"

        fun createIntent(
            context: Context,
            targetPackage: String,
            durationSeconds: Int,
            promptText: String
        ): Intent = Intent(context, MindfulPauseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PACKAGE_NAME, targetPackage)
            putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            putExtra(EXTRA_PROMPT_TEXT, promptText)
        }
    }
}
