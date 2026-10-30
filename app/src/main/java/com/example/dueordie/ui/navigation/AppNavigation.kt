package com.example.dueordie.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dueordie.R
import com.example.dueordie.ui.guilttrip.GuiltTripScreen
import com.example.dueordie.ui.notion.NotionSyncScreen
import com.example.dueordie.ui.permissions.PermissionsOnboardingScreen
import com.example.dueordie.ui.permissions.RulesAndBlocklistScreen
import com.example.dueordie.ui.task.TaskHomeScreen
import com.example.dueordie.ui.task.TaskViewModel
import com.example.dueordie.util.AppThemeMode
import com.example.dueordie.util.MoraHaptics
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import kotlinx.coroutines.launch

sealed class Screen {
    data object Onboarding : Screen()
    data object MainShell : Screen()
}

enum class MainTab(
    val label: String,
    val iconRes: Int
) {
    TASKS("Tasks", R.drawable.ic_nav_tasks),
    BALANCE("Balance", R.drawable.ic_nav_progress)
}

private data class ThemeDockPalette(
    val container: Color,
    val border: Color,
    val activePill: Color,
    val activeContent: Color,
    val inactiveContent: Color,
    val addTaskBg: Color,
    val addTaskBorder: Color,
    val addTaskText: Color
)

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
    var showSettingsScreen by remember { mutableStateOf(false) }
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
            val themeMode by OnboardingPreferences.themeModeFlow.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK, AppThemeMode.AMOLED -> true
                AppThemeMode.SYSTEM -> isSystemDark
            }
            val isAmoled = themeMode == AppThemeMode.AMOLED

            val dockPalette = when {
                isAmoled -> ThemeDockPalette(
                    container = Color(0xFF0E1611),
                    border = Color(0xFF203225),
                    activePill = Color(0xFF9CD2B0),
                    activeContent = Color(0xFF09120C),
                    inactiveContent = Color(0xFF6A8474),
                    addTaskBg = Color(0xFF0E1611),
                    addTaskBorder = Color(0xFF203225),
                    addTaskText = Color(0xFF9CD2B0)
                )
                isDark -> ThemeDockPalette(
                    container = Color(0xFF1A2720),
                    border = Color(0xFF2A3E31),
                    activePill = Color(0xFFFAF9F5),
                    activeContent = Color(0xFF142018),
                    inactiveContent = Color(0xFF859E8E),
                    addTaskBg = Color(0xFF1A2720),
                    addTaskBorder = Color(0xFF2A3E31),
                    addTaskText = Color(0xFFFAF9F5)
                )
                else -> ThemeDockPalette(
                    container = Color(0xFFFFFFFF),
                    border = Color(0xFFDCE5D8),
                    activePill = Color(0xFF2A4D3B),
                    activeContent = Color(0xFFFFFFFF),
                    inactiveContent = Color(0xFF67776C),
                    addTaskBg = Color(0xFFFFFFFF),
                    addTaskBorder = Color(0xFFDCE5D8),
                    addTaskText = Color(0xFF2A4D3B)
                )
            }

            if (showNotionSyncScreen) {
                BackHandler {
                    showNotionSyncScreen = false
                }
            } else if (showSettingsScreen) {
                BackHandler {
                    showSettingsScreen = false
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
                } else if (showSettingsScreen) {
                    RulesAndBlocklistScreen(
                        onNavigateBack = { showSettingsScreen = false },
                        onReplayIntroduction = {
                            currentScreen = Screen.Onboarding
                        },
                        onOpenNotionSync = {
                            showNotionSyncScreen = true
                        },
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
                                        selectedTab = MainTab.BALANCE
                                    },
                                    onNavigateToPermissions = {
                                        showSettingsScreen = true
                                    },
                                    viewModel = taskViewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            MainTab.BALANCE -> {
                                GuiltTripScreen(
                                    onNavigateBack = {
                                        selectedTab = MainTab.TASKS
                                    },
                                    onNavigateToSettings = {
                                        showSettingsScreen = true
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

                if (!showNotionSyncScreen && !showSettingsScreen) {
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

                    // Horizontal Dock Row: 2-tab pill on the left + wobbly "Add tasks" pill alongside it
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemeAwareBottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            overdueCount = taskUiState.overdueCount,
                            containerColor = dockPalette.container,
                            borderColor = dockPalette.border,
                            activePillColor = dockPalette.activePill,
                            activeContentColor = dockPalette.activeContent,
                            inactiveContentColor = dockPalette.inactiveContent
                        )

                        AnimatedVisibility(
                            visible = selectedTab == MainTab.TASKS,
                            enter = fadeIn(animationSpec = tween(180)) +
                                    expandHorizontally(
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        expandFrom = Alignment.Start
                                    ),
                            exit = fadeOut(animationSpec = tween(120)) +
                                    shrinkHorizontally(
                                        animationSpec = tween(150),
                                        shrinkTowards = Alignment.Start
                                    )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(10.dp))
                                WobblyAddTaskPill(
                                    onClick = { taskViewModel.openAddTaskDialog() },
                                    backgroundColor = dockPalette.addTaskBg,
                                    borderColor = dockPalette.addTaskBorder,
                                    textColor = dockPalette.addTaskText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wobbly "Add tasks" Pill.
 * Positioned alongside the 2-tab bottom dock capsule.
 * Features organic liquid jelly wobbly physics upon user interaction (press, release, tap).
 * Typography: "Add tasks" with no plus icon.
 */
@Composable
private fun WobblyAddTaskPill(
    onClick: () -> Unit,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val rotationZ = remember { Animatable(0f) }

    Surface(
        shape = RoundedCornerShape(29.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp,
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                this.scaleX = scaleX.value
                this.scaleY = scaleY.value
                this.rotationZ = rotationZ.value
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        MoraHaptics.performHapticTick(context)
                        // Refined tactile squish on press
                        coroutineScope.launch {
                            scaleX.animateTo(
                                1.04f,
                                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                        coroutineScope.launch {
                            scaleY.animateTo(
                                0.96f,
                                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                        coroutineScope.launch {
                            rotationZ.animateTo(
                                -1.5f,
                                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }

                        val released = try {
                            tryAwaitRelease()
                        } catch (e: Exception) {
                            false
                        }

                        // Gentle spring settling upon release
                        coroutineScope.launch {
                            scaleX.animateTo(
                                0.98f,
                                spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                            scaleX.animateTo(
                                1.0f,
                                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                        coroutineScope.launch {
                            scaleY.animateTo(
                                1.02f,
                                spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                            scaleY.animateTo(
                                1.0f,
                                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                        coroutineScope.launch {
                            rotationZ.animateTo(
                                1.0f,
                                spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                            rotationZ.animateTo(
                                0f,
                                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }

                        if (released) {
                            onClick()
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Add tasks",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Floating docked 2-tab theme-aware navbar pill (Tasks and Balance).
 * Adapts container and indicator colors dynamically to Light, Dark, and AMOLED themes.
 */
@Composable
private fun ThemeAwareBottomNavBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    overdueCount: Int,
    containerColor: Color,
    borderColor: Color,
    activePillColor: Color,
    activeContentColor: Color,
    inactiveContentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(29.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 8.dp,
        modifier = modifier
            .width(208.dp)
            .height(58.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 5.dp)
        ) {
            val tabCount = MainTab.entries.size
            val tabWidth = maxWidth / tabCount
            val selectedIndex = selectedTab.ordinal
            val targetLeft = tabWidth * selectedIndex
            val targetRight = targetLeft + tabWidth

            // Liquid dual-spring: the leading edge snaps forward while trailing edge lags,
            // creating an organic fluid stretch during transit that settles on arrival
            val leftSpring = spring<Dp>(
                stiffness = if (selectedIndex == 0) Spring.StiffnessMedium else Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
            val rightSpring = spring<Dp>(
                stiffness = if (selectedIndex == 1) Spring.StiffnessMedium else Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )

            val animatedLeft by animateDpAsState(
                targetValue = targetLeft,
                animationSpec = leftSpring,
                label = "navIndicatorLeft"
            )
            val animatedRight by animateDpAsState(
                targetValue = targetRight,
                animationSpec = rightSpring,
                label = "navIndicatorRight"
            )

            val indicatorWidth = (animatedRight - animatedLeft).coerceAtLeast(tabWidth * 0.85f)

            // Smooth sliding active cream/mint pill indicator with elastic stretch
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = activePillColor,
                modifier = Modifier
                    .offset(x = animatedLeft)
                    .width(indicatorWidth)
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
                        targetValue = if (isSelected) activeContentColor else inactiveContentColor,
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
                                    modifier = Modifier.size(17.dp)
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
