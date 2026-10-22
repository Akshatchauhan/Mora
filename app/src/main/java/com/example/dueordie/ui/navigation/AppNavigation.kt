package com.example.dueordie.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dueordie.R
import com.example.dueordie.ui.guilttrip.GuiltTripScreen
import com.example.dueordie.ui.permissions.PermissionsOnboardingScreen
import com.example.dueordie.ui.permissions.RulesAndBlocklistScreen
import com.example.dueordie.ui.task.TaskHomeScreen
import com.example.dueordie.ui.task.TaskViewModel
import com.example.dueordie.ui.notion.NotionSyncScreen
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper

sealed class Screen {
    data object Onboarding : Screen()
    data object MainShell : Screen()
}

enum class MainTab(
    val label: String,
    val iconRes: Int
) {
    TASKS("Tasks", R.drawable.ic_nav_tasks),
    SCREEN_TIME("Progress", R.drawable.ic_nav_progress),
    RULES("Settings", R.drawable.ic_nav_settings)
}

@Composable
fun AppNavigation(
    onboardingPrefs: OnboardingPreferences,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember {
        mutableStateOf<Screen>(
            if (onboardingPrefs.isOnboardingCompleted) Screen.MainShell else Screen.Onboarding
        )
    }

    var selectedTab by remember { mutableStateOf(MainTab.TASKS) }
    var showNotionSyncScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reducedMotion = remember(context) { PermissionHelper.isReducedMotionEnabled(context) }
    val taskViewModel: TaskViewModel = viewModel(
        factory = TaskViewModel.Factory(context)
    )

    when (currentScreen) {
        is Screen.Onboarding -> {
            PermissionsOnboardingScreen(
                onOnboardingComplete = { seedExampleTasks ->
                    if (seedExampleTasks) {
                        taskViewModel.addExampleTasks()
                    }
                    selectedTab = MainTab.TASKS
                    currentScreen = Screen.MainShell
                },
                modifier = modifier
            )
        }
        is Screen.MainShell -> {
            val taskUiState by taskViewModel.uiState.collectAsState()

            if (showNotionSyncScreen) {
                BackHandler {
                    showNotionSyncScreen = false
                }
            } else if (selectedTab != MainTab.TASKS) {
                BackHandler {
                    selectedTab = MainTab.TASKS
                }
            }

            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (showNotionSyncScreen) {
                    NotionSyncScreen(
                        onNavigateBack = { showNotionSyncScreen = false },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
                        label = "mainTabTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { activeTab ->
                        when (activeTab) {
                            MainTab.TASKS -> {
                                TaskHomeScreen(
                                    onNavigateToGuiltTrip = {
                                        selectedTab = MainTab.SCREEN_TIME
                                    },
                                    onNavigateToPermissions = {
                                        selectedTab = MainTab.RULES
                                    },
                                    viewModel = taskViewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            MainTab.SCREEN_TIME -> {
                                GuiltTripScreen(
                                    onNavigateBack = {
                                        selectedTab = MainTab.TASKS
                                    },
                                    onNavigateToSettings = {
                                        selectedTab = MainTab.RULES
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            MainTab.RULES -> {
                                RulesAndBlocklistScreen(
                                    onReplayIntroduction = {
                                        currentScreen = Screen.Onboarding
                                    },
                                    onOpenNotionSync = {
                                        showNotionSyncScreen = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Top frosted status bar blur scrim: softly diffuses content passing behind status bar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusBarTop + 24.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                0.65f to MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                                1.0f to Color.Transparent
                            )
                        )
                )

                if (!showNotionSyncScreen) {
                    // Bottom frosted navbar blur scrim: softly diffuses content passing behind floating navbar pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(navBarBottom + 88.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.40f to MaterialTheme.colorScheme.background.copy(alpha = 0.70f),
                                    1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                                )
                            )
                    )

                    ThemeAwareBottomNavBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        overdueCount = taskUiState.overdueCount,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

/**
 * Floating docked dark forest green navbar pill matching media_1791892426413_8f8618ca.png.
 * Outer pill: #15261E, 32dp corner radius.
 * Active tab: #FAF9F5 pill with #15261E bold text/icon laid out horizontally.
 * Inactive tabs: #8A9E92 muted sage text/icons laid out horizontally.
 * Equal 1f weight per tab ensures no word-wrapping ("Settings" never wraps).
 */
@Composable
private fun ThemeAwareBottomNavBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    overdueCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF15261E), // Dark forest green
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val tabCount = MainTab.entries.size
                val tabWidth = maxWidth / tabCount
                val selectedIndex = selectedTab.ordinal
                val animatedOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
                    label = "navIndicatorOffset"
                )

                // Smooth sliding light cream pill indicator
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFFFAF9F5), // Crisp light cream pill
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                ) {}

                // Interactive tab items positioned evenly
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF15261E) else Color(0xFF8A9E92),
                            animationSpec = tween(durationMillis = 180),
                            label = "navContentColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(tab) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (tab == MainTab.TASKS && overdueCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text(text = "$overdueCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = tab.iconRes),
                                        contentDescription = tab.label,
                                        tint = contentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    color = contentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}