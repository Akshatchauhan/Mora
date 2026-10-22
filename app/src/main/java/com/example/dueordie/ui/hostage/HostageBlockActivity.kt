package com.example.dueordie.ui.hostage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dueordie.MainActivity
import com.example.dueordie.ui.theme.DueOrDieTheme

class HostageBlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome()
            }
        })

        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Overdue task"
        val overdueMinutes = intent.getLongExtra(EXTRA_OVERDUE_MINUTES, 15L)
        val isCooldown = intent.getBooleanExtra(EXTRA_IS_COOLDOWN, false)
        val cooldownMinutes = intent.getIntExtra(EXTRA_COOLDOWN_MINUTES, 10)
        val headline = intent.getStringExtra(EXTRA_NUDGE_HEADLINE)
        val subtext = intent.getStringExtra(EXTRA_NUDGE_SUBTEXT)
        val badge = intent.getStringExtra(EXTRA_NUDGE_BADGE)

        setContent {
            DueOrDieTheme {
                HostageBlockScreen(
                    taskTitle = taskTitle,
                    overdueMinutes = overdueMinutes,
                    isCooldown = isCooldown,
                    cooldownMinutes = cooldownMinutes,
                    nudgeHeadline = headline,
                    nudgeSubtext = subtext,
                    nudgeBadge = badge,
                    onGoToTask = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onGoHome = {
                        navigateToHome()
                    }
                )
            }
        }
    }

    private fun navigateToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_OVERDUE_MINUTES = "extra_overdue_minutes"
        const val EXTRA_IS_COOLDOWN = "extra_is_cooldown"
        const val EXTRA_COOLDOWN_MINUTES = "extra_cooldown_minutes"
        const val EXTRA_NUDGE_HEADLINE = "extra_nudge_headline"
        const val EXTRA_NUDGE_SUBTEXT = "extra_nudge_subtext"
        const val EXTRA_NUDGE_BADGE = "extra_nudge_badge"

        fun createIntent(
            context: Context,
            taskTitle: String,
            overdueMinutes: Long,
            isCooldown: Boolean = false,
            cooldownMinutes: Int = 0,
            nudgeHeadline: String? = null,
            nudgeSubtext: String? = null,
            nudgeBadge: String? = null
        ): Intent {
            return Intent(context, HostageBlockActivity::class.java).apply {
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_OVERDUE_MINUTES, overdueMinutes)
                putExtra(EXTRA_IS_COOLDOWN, isCooldown)
                putExtra(EXTRA_COOLDOWN_MINUTES, cooldownMinutes)
                putExtra(EXTRA_NUDGE_HEADLINE, nudgeHeadline)
                putExtra(EXTRA_NUDGE_SUBTEXT, nudgeSubtext)
                putExtra(EXTRA_NUDGE_BADGE, nudgeBadge)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
    }
}

@Composable
fun HostageBlockScreen(
    taskTitle: String,
    overdueMinutes: Long,
    isCooldown: Boolean = false,
    cooldownMinutes: Int = 10,
    nudgeHeadline: String? = null,
    nudgeSubtext: String? = null,
    nudgeBadge: String? = null,
    onGoToTask: () -> Unit,
    onGoHome: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCooldown) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockClock,
                    contentDescription = "Focus lock active",
                    tint = if (isCooldown) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val displayHeadline = nudgeHeadline
                ?: if (isCooldown) "10-minute focus cooldown" else "Focus Lock is active"

            Text(
                text = displayHeadline,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            if (nudgeBadge != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = nudgeBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = taskTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val body = nudgeSubtext ?: if (isCooldown) {
                        "Because this task was overdue, distracting apps remain paused for $cooldownMinutes more minutes so your momentum sticks."
                    } else {
                        "Past due by $overdueMinutes minute${if (overdueMinutes == 1L) "" else "s"}. Complete this task in Mora to unlock apps."
                    }

                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onGoToTask,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Mora tasks",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onGoHome,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Home Screen",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
