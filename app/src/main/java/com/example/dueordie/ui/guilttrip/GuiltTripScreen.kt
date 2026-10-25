package com.example.dueordie.ui.guilttrip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dueordie.R
import com.example.dueordie.data.model.DayActivity
import com.example.dueordie.data.model.Task
import com.example.dueordie.util.AppUsageInfo
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.TimeFormatUtils

private val DonutChartColors = listOf(
    Color(0xFF234E39), // Forest Deep
    Color(0xFF4C7B5D), // Classic Sage
    Color(0xFF7FA78D), // Celadon Mint
    Color(0xFFD4C7B5), // Warm Sand
    Color(0xFFAE8B6D), // Warm Clay
    Color(0xFF638A77), // Slate Moss
    Color(0xFF8C9286)  // Muted Olive
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuiltTripScreen(
    @Suppress("UNUSED_PARAMETER") onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GuiltTripViewModel = viewModel(
        factory = GuiltTripViewModel.Factory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val onboardingPrefs = remember { OnboardingPreferences(context) }

    var showStreakModal by remember { mutableStateOf(false) }
    var selectedAppForConfig by remember { mutableStateOf<AppUsageInfo?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshGuiltTripData(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = statusBarTop + 16.dp,
            bottom = navBarBottom + 100.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Title & Top-Right Green Flame Streak Badge + Settings
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Mindful limits & screen balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Small Green Flame Streak Badge Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEAF3ED), // Calm soft sage container
                        border = BorderStroke(1.dp, Color(0xFFCBE0D3)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showStreakModal = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = "Streak & on-time stats",
                                tint = Color(0xFF2E6B4B), // Forest green flame
                                modifier = Modifier.size(18.dp)
                            )
                            if (uiState.streakDays > 0) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${uiState.streakDays}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = Color(0xFF2E6B4B)
                                )
                            }
                        }
                    }

                    // Subtle Refresh Button
                    IconButton(
                        onClick = { viewModel.refreshGuiltTripData(context) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh usage data",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings Gear Button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_settings),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Screen Time Donut / Pie Chart Dashboard
        item {
            ScreenTimeDonutDashboard(
                totalScreenTimeMs = uiState.totalScreenTimeMs,
                topApps = uiState.topUsedApps,
                isUsageTrackingEnabled = uiState.isUsageTrackingEnabled,
                isPermissionGranted = uiState.isUsagePermissionGranted,
                onEnableTracking = {
                    viewModel.setUsageTrackingEnabled(context, true)
                    if (!uiState.isUsagePermissionGranted) {
                        viewModel.openUsageAccessSettings(context)
                    }
                }
            )
        }

        // App Activity Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Most used apps",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tap any app to customize pauses and usage limits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.isUsageTrackingEnabled) {
                    TextButton(
                        onClick = { viewModel.setUsageTrackingEnabled(context, false) }
                    ) {
                        Text("Turn off", style = MaterialTheme.typography.labelMedium, softWrap = false)
                    }
                }
            }
        }

        // Screen Time Content List or Permissions Fallbacks
        if (!uiState.isUsageTrackingEnabled) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "App usage tracking is off",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Turn on tracking to view app screen times, mindful pause statistics, and configure extended usage reminders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.setUsageTrackingEnabled(context, true)
                                if (!uiState.isUsagePermissionGranted) {
                                    viewModel.openUsageAccessSettings(context)
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable app usage tracking")
                        }
                    }
                }
            }
        } else if (!uiState.isUsagePermissionGranted) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Usage Access permission required",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To display today's app activity and customize mindful pauses, allow Usage Access for Mora in Android settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.openUsageAccessSettings(context) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1B3B2B),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Open Usage Access settings")
                            }
                            TextButton(onClick = onNavigateToSettings) {
                                Text("Settings")
                            }
                        }
                    }
                }
            }
        } else if (uiState.topUsedApps.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "No foreground app usage recorded yet today",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.guiltTripMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.topUsedApps,
                key = { it.packageName }
            ) { appUsage ->
                val pauseCycles = onboardingPrefs.getPauseCyclesForPackage(appUsage.packageName)
                val isPauseEnabled = onboardingPrefs.isAppPauseEnabled(appUsage.packageName)
                val dailyLimitMins = onboardingPrefs.getAppDailyLimitMinutes(appUsage.packageName)

                AppActivityInteractiveRow(
                    appUsage = appUsage,
                    isPauseEnabled = isPauseEnabled,
                    pauseCycles = pauseCycles,
                    dailyLimitMinutes = dailyLimitMins,
                    onClick = { selectedAppForConfig = appUsage }
                )
            }
        }

        // Past-due tasks section (if any)
        if (uiState.overdueTasks.isNotEmpty()) {
            item {
                Text(
                    text = "Tasks past due (${uiState.overdueTasks.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(
                items = uiState.overdueTasks,
                key = { it.id }
            ) { task ->
                OverdueTaskActionRow(
                    task = task,
                    onMarkComplete = { viewModel.markTaskCompleted(task.id) }
                )
            }
        }
    }

    // Streak & Performance Modal (opened by top-right green flame badge)
    if (showStreakModal) {
        StreakPerformanceModal(
            streakDays = uiState.streakDays,
            weeklyActivity = uiState.weeklyActivity,
            onDismiss = { showStreakModal = false }
        )
    }

    // App Customization Bottom Sheet (opened by tapping any app)
    selectedAppForConfig?.let { app ->
        AppCustomizationBottomSheet(
            app = app,
            onboardingPrefs = onboardingPrefs,
            onDismiss = { selectedAppForConfig = null },
            onUpdatePauseCycles = { pkg, cycles ->
                viewModel.updateAppPauseCycles(pkg, cycles)
            },
            onUpdateDailyLimit = { pkg, limit ->
                viewModel.updateAppDailyLimit(pkg, limit)
            },
            onTogglePause = { pkg, enabled ->
                viewModel.toggleAppPause(pkg, enabled)
            }
        )
    }
}

/**
 * Digital Wellbeing style screen time card with clean Donut / Pie Chart canvas.
 * Matches layout from media_1791909709119_06af7317.png.
 */
@Composable
private fun ScreenTimeDonutDashboard(
    totalScreenTimeMs: Long,
    topApps: List<AppUsageInfo>,
    isUsageTrackingEnabled: Boolean,
    isPermissionGranted: Boolean,
    onEnableTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dropdown / Category Tag
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEAF3ED),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Screen time",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF2E6B4B),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            // Big Bold Screen Time
            val formattedScreenTime = TimeFormatUtils.formatScreenTime(totalScreenTimeMs)
            Text(
                text = formattedScreenTime,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Donut Canvas or Permission Request
            if (!isUsageTrackingEnabled || !isPermissionGranted) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(onClick = onEnableTracking),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to enable\nusage access",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (topApps.isEmpty() || totalScreenTimeMs <= 0L) {
                // Calm empty donut placeholder
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color(0xFFE5EDE7),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "No usage\nrecorded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Interactive Donut Chart
                Box(
                    modifier = Modifier.size(170.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val strokePx = 24.dp.toPx()
                        var currentStartAngle = -90f
                        val gapAngle = 3f
                        val totalApps = topApps.take(6)
                        val totalAppTime = totalApps.sumOf { it.usageTimeMs }.toFloat()

                        totalApps.forEachIndexed { index, app ->
                            val color = DonutChartColors[index % DonutChartColors.size]
                            val fraction = (app.usageTimeMs.toFloat() / totalAppTime)
                            val rawSweep = fraction * 360f
                            val sweepAngle = (rawSweep - gapAngle).coerceAtLeast(1.5f)

                            drawArc(
                                color = color,
                                startAngle = currentStartAngle + (gapAngle / 2f),
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                            currentStartAngle += rawSweep
                        }
                    }

                    // Center information
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${topApps.size}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chart Legend (Top 3 apps)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewApps = topApps.take(3)
                    previewApps.forEachIndexed { index, app ->
                        val color = DonutChartColors[index % DonutChartColors.size]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive App Activity Row with custom badges for mindful pause & usage limit.
 */
@Composable
private fun AppActivityInteractiveRow(
    appUsage: AppUsageInfo,
    isPauseEnabled: Boolean,
    pauseCycles: Int,
    dailyLimitMinutes: Int,
    onClick: () -> Unit
) {
    val iconBitmap = remember(appUsage.packageName, appUsage.icon) {
        try {
            appUsage.icon?.toBitmap(width = 64, height = 64)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = "${appUsage.appName} icon",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // App Name & Usage Duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appUsage.appName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appUsage.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isPauseEnabled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEAF3ED)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Spa,
                                contentDescription = null,
                                tint = Color(0xFF2E6B4B),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$pauseCycles cycles",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF2E6B4B)
                            )
                        }
                    }
                }

                if (dailyLimitMinutes > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF6EFE6)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassBottom,
                                contentDescription = null,
                                tint = Color(0xFF8B643A),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${dailyLimitMinutes}m",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF8B643A)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Slide-up Bottom Sheet for app customization (breath cycles 1-4, extended usage reminders).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppCustomizationBottomSheet(
    app: AppUsageInfo,
    onboardingPrefs: OnboardingPreferences,
    onDismiss: () -> Unit,
    onUpdatePauseCycles: (packageName: String, cycles: Int) -> Unit,
    onUpdateDailyLimit: (packageName: String, limitMinutes: Int) -> Unit,
    onTogglePause: (packageName: String, enabled: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isPauseEnabled by remember(app.packageName) {
        mutableStateOf(onboardingPrefs.isAppPauseEnabled(app.packageName))
    }
    var currentCycles by remember(app.packageName) {
        mutableIntStateOf(onboardingPrefs.getPauseCyclesForPackage(app.packageName))
    }
    var currentLimitMinutes by remember(app.packageName) {
        mutableIntStateOf(onboardingPrefs.getAppDailyLimitMinutes(app.packageName))
    }
    var isLimitEnabled by remember(app.packageName) {
        mutableStateOf(currentLimitMinutes > 0)
    }

    val iconBitmap = remember(app.packageName, app.icon) {
        try {
            app.icon?.toBitmap(width = 64, height = 64)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = "${app.appName} icon",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Used today: ${app.formattedTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Section 1: Mindful Pause before opening
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mindful Pause on launch",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Requires slow breaths before this app opens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPauseEnabled,
                        onCheckedChange = { checked ->
                            isPauseEnabled = checked
                            onTogglePause(app.packageName, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2E6B4B)
                        )
                    )
                }

                AnimatedVisibility(visible = isPauseEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "BREATH CYCLES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 3, 4).forEach { count ->
                                val selected = currentCycles == count
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0xFF1B3B2B) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            currentCycles = count
                                            onUpdatePauseCycles(app.packageName, count)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${count * 4}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) Color(0xFFB7DCC2) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Section 2: Continuous usage reminder
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extended usage reminder",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatic overlay triggers when used continuously",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isLimitEnabled,
                        onCheckedChange = { checked ->
                            isLimitEnabled = checked
                            if (!checked) {
                                currentLimitMinutes = 0
                                onUpdateDailyLimit(app.packageName, 0)
                            } else {
                                val defaultMins = if (currentLimitMinutes > 0) currentLimitMinutes else 30
                                currentLimitMinutes = defaultMins
                                onUpdateDailyLimit(app.packageName, defaultMins)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2E6B4B)
                        )
                    )
                }

                AnimatedVisibility(visible = isLimitEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "REMIND AFTER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val limitOptions = listOf(
                            15 to "15m",
                            30 to "30m",
                            45 to "45m",
                            60 to "1h",
                            90 to "1.5h",
                            120 to "2h"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            limitOptions.take(3).forEach { (mins, label) ->
                                val selected = currentLimitMinutes == mins
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0xFF1B3B2B) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            currentLimitMinutes = mins
                                            onUpdateDailyLimit(app.packageName, mins)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            limitOptions.drop(3).forEach { (mins, label) ->
                                val selected = currentLimitMinutes == mins
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0xFF1B3B2B) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            currentLimitMinutes = mins
                                            onUpdateDailyLimit(app.packageName, mins)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Save / Done Button
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B3B2B),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = "Save changes",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * Slide-up Streak modal opened by top-right green flame badge.
 * Displays green flame, streak days, and 7-day completion bar graph.
 * Users slide down to dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreakPerformanceModal(
    streakDays: Int,
    weeklyActivity: List<DayActivity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Big Green Flame Badge Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF3ED)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Whatshot,
                    contentDescription = null,
                    tint = Color(0xFF2E6B4B),
                    modifier = Modifier.size(38.dp)
                )
            }

            // Streak Title & Subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val streakText = "$streakDays Day${if (streakDays == 1) "" else "s"}"
                Text(
                    text = streakText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Consecutive days completing tasks on time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Minimalist 7-Day Weekly Bar Graph
            WeeklyCompletionBarGraph(weeklyActivity = weeklyActivity)
        }
    }
}

/**
 * Minimalist 7-Day Completion Bar Graph (Mon-Sun).
 */
@Composable
private fun WeeklyCompletionBarGraph(
    weeklyActivity: List<DayActivity>,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "7-day activity",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "On-time completions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val maxCompletions = maxOf(weeklyActivity.maxOfOrNull { it.completedCount } ?: 1, 1)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyActivity.forEach { day ->
                    val fraction = (day.onTimeCount.toFloat() / maxCompletions.toFloat()).coerceIn(0.08f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (day.onTimeCount > 0) {
                            Text(
                                text = "${day.onTimeCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (day.isToday) Color(0xFF2E6B4B) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Vertical Bar
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height((60.dp * fraction).coerceAtLeast(6.dp))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    if (day.isToday) Color(0xFF2E6B4B)
                                    else if (day.onTimeCount > 0) Color(0xFF7FA78D)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label (Mon, Tue, etc.)
                        Text(
                            text = day.dayOfWeek.take(3),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = if (day.isToday) Color(0xFF2E6B4B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverdueTaskActionRow(
    task: Task,
    onMarkComplete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Due ${TimeFormatUtils.formatDeadlineDateTime(task.deadlineTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                onClick = onMarkComplete,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Complete")
            }
        }
    }
}
