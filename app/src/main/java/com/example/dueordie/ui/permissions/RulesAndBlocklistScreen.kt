package com.example.dueordie.ui.permissions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.media.RingtoneManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.example.dueordie.R
import com.example.dueordie.data.local.AppDatabase
import com.example.dueordie.data.model.MindfulPauseConfig
import com.example.dueordie.data.model.PauseFeatureStatus
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.service.OngoingNotificationService
import com.example.dueordie.util.MoraHaptics
import com.example.dueordie.util.NotificationHelper
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PermissionHelper
import com.example.dueordie.util.PausePing
import com.example.dueordie.worker.WorkManagerScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RulesAndBlocklistScreen(
    onReplayIntroduction: () -> Unit = {},
    onOpenNotionSync: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = viewModel(
        factory = PermissionsViewModel.Factory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val onboardingPrefs = remember { OnboardingPreferences(context) }

    var themeMode by remember { mutableStateOf(onboardingPrefs.themeMode) }
    var showTutorialHints by remember { mutableStateOf(onboardingPrefs.showTutorialHints) }
    var hasSeenPauseSetupTip by remember { mutableStateOf(onboardingPrefs.hasSeenPauseSetupTip) }
    var showPauseSettingsTour by remember { mutableStateOf(false) }
    var pauseSettingsTourStep by remember { mutableIntStateOf(0) }
    var pauseOptionsExpanded by remember { mutableStateOf(false) }
    var repeatOptionsExpanded by remember { mutableStateOf(false) }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    var pausePingEnabled by remember { mutableStateOf(onboardingPrefs.isPausePingEnabled) }
    var tutorialResetFeedback by remember { mutableStateOf(false) }
    var showHowItWorksDialog by remember { mutableStateOf(false) }

    var areRemindersEnabled by remember { mutableStateOf(onboardingPrefs.areRemindersEnabled) }
    var isPersistentNotificationEnabled by remember { mutableStateOf(onboardingPrefs.isPersistentNotificationEnabled) }
    var isOverdueAlarmEnabled by remember { mutableStateOf(onboardingPrefs.isOverdueAlarmEnabled) }
    var isHiddenTaskDeletionEnabled by remember { mutableStateOf(onboardingPrefs.isHiddenTaskDeletionEnabled) }
    var isUsageTrackingEnabled by remember { mutableStateOf(onboardingPrefs.isUsageTrackingEnabled) }
    var isFocusLockEnabled by remember { mutableStateOf(onboardingPrefs.isGlobalHostageModeEnabled) }
    var blockedPackages by remember { mutableStateOf(onboardingPrefs.blockedPackages) }
    var showSelectAppsDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val appDatabase = remember(context) { AppDatabase.getDatabase(context) }
    val allTasksList by appDatabase.taskDao().getAllTasks().collectAsState(initial = emptyList())

    // Mindful Pause ("Pause before apps") settings
    var isMindfulPauseEnabled by remember { mutableStateOf(onboardingPrefs.isMindfulPauseEnabled) }
    var pauseSelectedPackages by remember { mutableStateOf(onboardingPrefs.pauseSelectedPackages) }
    var disabledReInterventionPackages by remember { mutableStateOf(onboardingPrefs.disabledReInterventionPackages) }
    var pauseDurationSeconds by remember { mutableIntStateOf(onboardingPrefs.pauseDurationSeconds) }
    var isReInterventionEnabled by remember { mutableStateOf(onboardingPrefs.isReInterventionEnabled) }
    var reInterventionIntervalMinutes by remember { mutableIntStateOf(onboardingPrefs.reInterventionIntervalMinutes) }
    var returnGraceSeconds by remember { mutableIntStateOf(onboardingPrefs.returnGraceSeconds) }

    var showSelectPauseAppsDialog by remember { mutableStateOf(false) }
    var showPausePreviewDialog by remember { mutableStateOf(false) }
    var showPrivacyDisclosure by remember { mutableStateOf(false) }
    var isRealAccessibilityEnabled by remember {
        mutableStateOf(PermissionHelper.isRealAccessibilityServiceEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions(context)
                isRealAccessibilityEnabled = PermissionHelper.isRealAccessibilityServiceEnabled(context)
                themeMode = onboardingPrefs.themeMode
                showTutorialHints = onboardingPrefs.showTutorialHints
                hasSeenPauseSetupTip = onboardingPrefs.hasSeenPauseSetupTip
                pausePingEnabled = onboardingPrefs.isPausePingEnabled
                areRemindersEnabled = onboardingPrefs.areRemindersEnabled
                isPersistentNotificationEnabled = onboardingPrefs.isPersistentNotificationEnabled
                isOverdueAlarmEnabled = onboardingPrefs.isOverdueAlarmEnabled
                isHiddenTaskDeletionEnabled = onboardingPrefs.isHiddenTaskDeletionEnabled
                isUsageTrackingEnabled = onboardingPrefs.isUsageTrackingEnabled
                isFocusLockEnabled = onboardingPrefs.isGlobalHostageModeEnabled
                blockedPackages = onboardingPrefs.blockedPackages
                isMindfulPauseEnabled = onboardingPrefs.isMindfulPauseEnabled
                pauseSelectedPackages = onboardingPrefs.pauseSelectedPackages
                disabledReInterventionPackages = onboardingPrefs.disabledReInterventionPackages
                pauseDurationSeconds = onboardingPrefs.pauseDurationSeconds
                isReInterventionEnabled = onboardingPrefs.isReInterventionEnabled
                reInterventionIntervalMinutes = onboardingPrefs.reInterventionIntervalMinutes
                returnGraceSeconds = onboardingPrefs.returnGraceSeconds
                isIgnoringBatteryOptimizations =
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.checkPermissions(context)
        OngoingNotificationService.startOrUpdate(context)
    }

    val requestNotificationPermissionIfNeeded = {
        if (!PermissionHelper.isNotificationPermissionGranted(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.openNotificationSettings(context)
            }
        }
    }

    val requestAccessibilityWithDisclosure = {
        viewModel.openAccessibilitySettings(context)
    }

    val pauseFeatureStatus = MindfulPauseConfig(
        isPauseEnabled = isMindfulPauseEnabled,
        isAccessibilityServiceEnabled = uiState.isAccessibilityServiceEnabled
    ).featureStatus

    val settingsChoiceChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.primary
    )

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Figma Page 1 background #F7F7F1
            .verticalScroll(rememberScrollState())
            .padding(
                top = statusBarTop + 16.dp,
                bottom = navBarBottom + 100.dp,
                start = 16.dp,
                end = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Heading matching 3:859
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ==========================================
        // 1. NOTION INTEGRATION CARD (Top Section)
        // ==========================================
        FigmaSectionLabel(text = "Integrations")

        FigmaSettingCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotionSync)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notion),
                            contentDescription = "Notion",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Notion Integration",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Two-way task sync with your Notion workspace.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open Notion settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ==========================================
        // 2. MINDFUL PAUSE GROUP (Figma 9:82 - 3:887)
        // ==========================================
        FigmaSectionLabel(text = "Mindful pause")

        FigmaSettingCard {
            // Mindful Pause Master Row (9:82)
            FigmaSettingRow(
                icon = Icons.Rounded.PauseCircle,
                iconBg = MaterialTheme.colorScheme.primary,
                iconTint = MaterialTheme.colorScheme.onPrimary,
                title = "Mindful pause",
                configuration = if (isMindfulPauseEnabled) "On" else "Off",
                onClick = {
                    val next = !isMindfulPauseEnabled
                    isMindfulPauseEnabled = next
                    onboardingPrefs.isMindfulPauseEnabled = next
                    if (next) {
                        if (!uiState.isAccessibilityServiceEnabled) {
                            requestAccessibilityWithDisclosure()
                        }
                        OngoingNotificationService.startOrUpdate(context)
                    }
                }
            ) {
                PauseStatusBadge(status = pauseFeatureStatus)
            }

            FigmaDivider()

            // Pause before apps switch (9:94)
            FigmaSettingRow(
                icon = Icons.Rounded.PauseCircle,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Pause before apps",
                configuration = "Take two slow breaths before opening apps you choose."
            ) {
                FigmaSwitch(
                    checked = isMindfulPauseEnabled,
                    onCheckedChange = { enabled ->
                        isMindfulPauseEnabled = enabled
                        onboardingPrefs.isMindfulPauseEnabled = enabled
                        if (enabled) {
                            if (!uiState.isAccessibilityServiceEnabled) {
                                requestAccessibilityWithDisclosure()
                            }
                            OngoingNotificationService.startOrUpdate(context)
                        }
                    }
                )
            }

            AnimatedVisibility(visible = isMindfulPauseEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!uiState.isAccessibilityServiceEnabled) {
                        Surface(
                            onClick = requestAccessibilityWithDisclosure,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Background checker permission required",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Tap to grant Usage Access and Display Over Apps so Mora can pause apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    FigmaDivider()

                    // Choose apps (3:862)
                    FigmaSettingRow(
                        icon = Icons.Rounded.GridView,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Choose apps (${pauseSelectedPackages.size})",
                        configuration = "Select apps that start with a brief breathing pause.",
                        onClick = { showSelectPauseAppsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Choose apps",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FigmaDivider()

                    // Pause duration row (3:870)
                    FigmaSettingRow(
                        icon = Icons.Rounded.PauseCircle,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Pause duration",
                        configuration = "${pauseDurationSeconds}s - Mindful breathing pause",
                        onClick = { pauseOptionsExpanded = !pauseOptionsExpanded }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (pauseOptionsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = if (pauseOptionsExpanded) 180f else 0f }
                        )
                    }

                    AnimatedVisibility(visible = pauseOptionsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Pause duration",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Take two calm breaths before opening paused apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OnboardingPreferences.PAUSE_DURATION_OPTIONS.forEach { seconds ->
                                    FilterChip(
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                        colors = settingsChoiceChipColors,
                                        selected = pauseDurationSeconds == seconds,
                                        onClick = {
                                            pauseDurationSeconds = seconds
                                            onboardingPrefs.pauseDurationSeconds = seconds
                                        },
                                        label = { Text("${seconds}s", maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }

                    FigmaDivider()

                    // Repeat & switching (3:880)
                    FigmaSettingRow(
                        icon = Icons.Rounded.Repeat,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Repeat and switching",
                        configuration = if (isReInterventionEnabled) {
                            "${reInterventionIntervalMinutes} min repeat, ${returnGraceSeconds}s return grace"
                        } else {
                            "Repeat off, ${returnGraceSeconds}s return grace"
                        },
                        onClick = { repeatOptionsExpanded = !repeatOptionsExpanded }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (repeatOptionsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = if (repeatOptionsExpanded) 180f else 0f }
                        )
                    }

                    AnimatedVisibility(visible = repeatOptionsExpanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SettingSwitchRow(
                                title = "Show again",
                                description = "Repeat the pause while you stay in a selected app.",
                                checked = isReInterventionEnabled,
                                onCheckedChange = { enabled ->
                                    isReInterventionEnabled = enabled
                                    onboardingPrefs.isReInterventionEnabled = enabled
                                }
                            )
                            if (isReInterventionEnabled) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Repeat every", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OnboardingPreferences.REINTERVENTION_MINUTE_OPTIONS.forEach { minutes ->
                                        FilterChip(
                                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                            colors = settingsChoiceChipColors,
                                            selected = reInterventionIntervalMinutes == minutes,
                                            onClick = {
                                                reInterventionIntervalMinutes = minutes
                                                onboardingPrefs.reInterventionIntervalMinutes = minutes
                                            },
                                            label = { Text("${minutes}m", maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FigmaDivider()

                    // Advanced section for Mindful Pause
                    Text(
                        text = "ADVANCED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    FigmaSettingRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        iconBg = if (isRealAccessibilityEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = if (isRealAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Accessibility service",
                        configuration = if (isRealAccessibilityEnabled) "Active. Zero delay pause detection." else "Optional. Tap to enable instant zero delay pause.",
                        onClick = {
                            PermissionHelper.openRealAccessibilitySettings(context)
                        }
                    ) {
                        if (isRealAccessibilityEnabled) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "On",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Enable Accessibility",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Breathing preview card matching Figma 3:887
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Preview breathing screen",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Test your ${pauseDurationSeconds}s pause inside the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ringColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer ring
                            drawCircle(
                                color = ringColor,
                                radius = size.minDimension / 2f - 1.dp.toPx(),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            // Inner ring
                            drawCircle(
                                color = ringColor,
                                radius = size.minDimension / 2f - 7.dp.toPx(),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                        Text(
                            text = "${pauseDurationSeconds}s",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Preview Action button (3:896)
                    Surface(
                        onClick = { showPausePreviewDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. NOTIFICATIONS GROUP (Figma 9:106 - 9:107)
        // ==========================================
        FigmaSectionLabel(text = "Notifications")

        FigmaSettingCard {
            // Deadline reminders (9:108)
            FigmaSettingRow(
                icon = Icons.Rounded.Notifications,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Deadline reminders",
                configuration = "Check-ins and due-soon alerts for active tasks."
            ) {
                FigmaSwitch(
                    checked = areRemindersEnabled && uiState.isNotificationPermissionGranted,
                    onCheckedChange = { enabled ->
                        areRemindersEnabled = enabled
                        onboardingPrefs.areRemindersEnabled = enabled
                        if (enabled) {
                            requestNotificationPermissionIfNeeded()
                            WorkManagerScheduler.scheduleDailyReminders(context)
                        } else {
                            WorkManagerScheduler.cancelDailyReminders(context)
                        }
                    }
                )
            }

            FigmaDivider()

            // Persistent status notification (9:121)
            FigmaSettingRow(
                icon = Icons.Rounded.NotificationsActive,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Persistent status notification",
                configuration = "Show the next upcoming deadline in the notification drawer."
            ) {
                FigmaSwitch(
                    checked = isPersistentNotificationEnabled && uiState.isNotificationPermissionGranted,
                    onCheckedChange = { enabled ->
                        isPersistentNotificationEnabled = enabled
                        onboardingPrefs.isPersistentNotificationEnabled = enabled
                        if (enabled) {
                            requestNotificationPermissionIfNeeded()
                        }
                        OngoingNotificationService.startOrUpdate(context)
                    }
                )
            }

            FigmaDivider()

            // Overdue task alarm
            FigmaSettingRow(
                icon = Icons.Rounded.NotificationsActive,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Overdue task alarm",
                configuration = "Sound an audible alarm if a task passes its deadline without being updated."
            ) {
                FigmaSwitch(
                    checked = isOverdueAlarmEnabled && uiState.isNotificationPermissionGranted,
                    onCheckedChange = { enabled ->
                        isOverdueAlarmEnabled = enabled
                        onboardingPrefs.isOverdueAlarmEnabled = enabled
                        if (enabled) {
                            requestNotificationPermissionIfNeeded()
                        }
                    }
                )
            }

            if (isOverdueAlarmEnabled) {
                FigmaDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Test alarm sound",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Play a short preview of the audible overdue alarm.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        onClick = {
                            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            val ringtone = RingtoneManager.getRingtone(context, uri)
                            ringtone?.play()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                runCatching { ringtone?.stop() }
                            }, 2500L)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. BACKGROUND RELIABILITY (Figma 9:132 - 9:133)
        // ==========================================
        FigmaSectionLabel(text = "Background reliability")

        FigmaSettingCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryAlert,
                            contentDescription = null,
                            tint = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isIgnoringBatteryOptimizations) "Unrestricted background use" else "Battery optimization is on",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isIgnoringBatteryOptimizations) {
                                "Background reminders and app pauses can run with fewer battery limits."
                            } else {
                                "Battery saver may delay reminders or app launch detection on some phones."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    onClick = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            }
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Battery settings",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. SCREEN TIME & FOCUS LOCK (Figma 9:145 - 9:146)
        // ==========================================
        FigmaSectionLabel(text = "Screen time & Focus lock")

        FigmaSettingCard {
            // Track app usage (9:147)
            FigmaSettingRow(
                icon = Icons.Rounded.QueryStats,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Track app usage",
                configuration = "Show today's most-used apps on the Progress screen."
            ) {
                FigmaSwitch(
                    checked = isUsageTrackingEnabled && uiState.isUsageStatsPermissionGranted,
                    onCheckedChange = { enabled ->
                        isUsageTrackingEnabled = enabled
                        onboardingPrefs.isUsageTrackingEnabled = enabled
                        if (enabled && !uiState.isUsageStatsPermissionGranted) {
                            viewModel.openUsageAccessSettings(context)
                        }
                    }
                )
            }

            FigmaDivider()

            // Focus lock (9:160)
            FigmaSettingRow(
                icon = Icons.Rounded.Lock,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Focus lock",
                configuration = "Show a reminder when selected apps open while a task is overdue."
            ) {
                FigmaSwitch(
                    checked = isFocusLockEnabled && uiState.isAccessibilityServiceEnabled,
                    onCheckedChange = { enabled ->
                        isFocusLockEnabled = enabled
                        onboardingPrefs.isGlobalHostageModeEnabled = enabled
                        if (enabled && !uiState.isAccessibilityServiceEnabled) {
                            requestAccessibilityWithDisclosure()
                        }
                        OngoingNotificationService.startOrUpdate(context)
                    }
                )
            }

            AnimatedVisibility(visible = isFocusLockEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FigmaDivider()

                    // Selected apps (13:3)
                    FigmaSettingRow(
                        icon = Icons.Rounded.GridView,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Selected apps (${blockedPackages.size})",
                        configuration = "Apps that show a reminder when a Focus lock task is overdue.",
                        onClick = { showSelectAppsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Choose apps",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }


        // ==========================================
        // 6. APPEARANCE (Figma 13:13 - 13:16)
        // ==========================================
        FigmaSectionLabel(text = "Appearance")

        FigmaSettingCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 2x2 grid matching 13:16
                com.example.dueordie.util.AppThemeMode.entries.chunked(2).forEachIndexed { rowIndex, rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowModes.forEach { mode ->
                            val isSelected = themeMode == mode
                            Surface(
                                onClick = {
                                    themeMode = mode
                                    onboardingPrefs.themeMode = mode
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = mode.displayLabel,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 15.sp
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    if (rowIndex == 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        // ==========================================
        // 7. SOUND & MOTION (Figma 13:27 - 13:28)
        // ==========================================
        FigmaSectionLabel(text = "Sound & motion")

        FigmaSettingCard {
            // Soft tone (13:29)
            FigmaSettingRow(
                icon = Icons.Rounded.VolumeUp,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Soft pause tone",
                configuration = "Play one quiet ping when the breathing pause begins."
            ) {
                FigmaSwitch(
                    checked = pausePingEnabled,
                    onCheckedChange = { enabled ->
                        pausePingEnabled = enabled
                        onboardingPrefs.isPausePingEnabled = enabled
                    }
                )
            }

            FigmaDivider()

            // Preview tone (13:39)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preview tone",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    onClick = { PausePing.play(context) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            FigmaDivider()

            // Reduced motion info (13:49)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Reduced motion",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Breathing motion follows your Android reduced-motion setting. The pause remains usable without animation.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ==========================================
        // 8. HELP & TUTORIALS (Figma 13:53 - 13:54)
        // ==========================================
        FigmaSectionLabel(text = "Help & tutorials")

        FigmaSettingCard {
            // How it works (13:55)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHowItWorksDialog = true }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tasks, progress and mindful pauses.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            FigmaDivider()

            // Show tutorial hints (13:63)
            FigmaSettingRow(
                icon = null,
                title = "Show tutorial hints",
                configuration = "Display quick tour and one-time setup tips."
            ) {
                FigmaSwitch(
                    checked = showTutorialHints,
                    onCheckedChange = { enabled ->
                        showTutorialHints = enabled
                        onboardingPrefs.showTutorialHints = enabled
                    }
                )
            }

            FigmaDivider()

            // Replay intro & Reset tutorials buttons (13:73)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onReplayIntroduction,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Replay intro",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    onClick = {
                        onboardingPrefs.resetTutorials()
                        showTutorialHints = true
                        hasSeenPauseSetupTip = false
                        tutorialResetFeedback = true
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Reset tutorials",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            FigmaDivider()

            // Pause settings tour (13:80)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pauseSettingsTourStep = 0; showPauseSettingsTour = true }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pause settings tour",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ==========================================
        // 9. ABOUT PERMISSIONS & PRIVACY (Figma 13:84)
        // ==========================================
        FigmaSettingCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPrivacyDisclosure = true }
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "About permissions & privacy",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ==========================================
        // 10. TASK DELETION & RECOVERY
        // ==========================================
        FigmaSectionLabel(text = "Task deletion")

        FigmaSettingCard {
            FigmaSettingRow(
                icon = Icons.Rounded.Delete,
                iconBg = MaterialTheme.colorScheme.errorContainer,
                title = "Delete tasks in Settings only",
                configuration = if (isHiddenTaskDeletionEnabled) {
                    "Enabled. Tasks cannot be deleted on the task page. Deletion is only allowed here in Settings with a 5-second hold."
                } else {
                    "Disabled. Tasks can be deleted by swiping left on the task page. Turn on to remove deletion from the task page."
                }
            ) {
                FigmaSwitch(
                    checked = isHiddenTaskDeletionEnabled,
                    onCheckedChange = { enabled ->
                        isHiddenTaskDeletionEnabled = enabled
                        onboardingPrefs.isHiddenTaskDeletionEnabled = enabled
                    }
                )
            }

            if (isHiddenTaskDeletionEnabled) {
                FigmaDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Hold to delete tasks",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Press and hold the delete button for 5 seconds to permanently delete a task.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (allTasksList.isEmpty()) {
                        Text(
                            text = "No tasks currently exist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        allTasksList.forEachIndexed { index, task ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            TaskHiddenDeletionRow(
                                task = task,
                                onDeleteConfirmed = {
                                    scope.launch {
                                        appDatabase.taskDao().deleteTask(task)
                                        NotificationHelper.cancelOverdueAlarmNotification(context, task.id)
                                        OngoingNotificationService.startOrUpdate(context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Dialogs
    if (showPrivacyDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showPrivacyDisclosure = false
                viewModel.openAccessibilitySettings(context)
            },
            onDecline = { showPrivacyDisclosure = false }
        )
    }

    if (showPauseSettingsTour) {
        PauseSettingsTourDialog(
            step = pauseSettingsTourStep,
            onBack = { if (pauseSettingsTourStep > 0) pauseSettingsTourStep -= 1 },
            onContinue = {
                if (pauseSettingsTourStep < 2) pauseSettingsTourStep += 1 else showPauseSettingsTour = false
            },
            onSkip = { showPauseSettingsTour = false }
        )
    }

    if (showHowItWorksDialog) {
        HowItWorksDialog(onDismiss = { showHowItWorksDialog = false })
    }

    if (showSelectAppsDialog) {
        SelectBlockedAppsDialog(
            initialSelectedPackages = blockedPackages,
            onDismiss = { showSelectAppsDialog = false },
            onSave = { updatedSet ->
                blockedPackages = updatedSet
                onboardingPrefs.blockedPackages = updatedSet
                showSelectAppsDialog = false
            }
        )
    }

    if (showSelectPauseAppsDialog) {
        SelectPauseAppsDialog(
            initialSelectedPackages = pauseSelectedPackages,
            initialDisabledReInterventionPackages = disabledReInterventionPackages,
            isReInterventionEnabled = isReInterventionEnabled,
            onDismiss = { showSelectPauseAppsDialog = false },
            onSave = { updatedSelected, updatedDisabledReIntervention ->
                pauseSelectedPackages = updatedSelected
                disabledReInterventionPackages = updatedDisabledReIntervention
                onboardingPrefs.pauseSelectedPackages = updatedSelected
                onboardingPrefs.disabledReInterventionPackages = updatedDisabledReIntervention
                showSelectPauseAppsDialog = false
            }
        )
    }

    if (showPausePreviewDialog) {
        MindfulPausePreviewDialog(
            durationSeconds = pauseDurationSeconds,
            promptText = "", // Blank triggers 100-prompt repository rotation
            playPingOnStart = pausePingEnabled,
            onDismiss = { showPausePreviewDialog = false }
        )
    }
}

@Composable
private fun FigmaSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
    )
}

@Composable
private fun FigmaSettingCard(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        content = content
    )
}

@Composable
private fun FigmaSettingRow(
    icon: ImageVector? = null,
    iconBg: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    configuration: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = configuration,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
private fun FigmaDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun FigmaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
private fun PauseSettingsTourDialog(
    step: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val titles = listOf("Choose apps", "Set the pause", "Repeat & return")
    val descriptions = listOf(
        "Choose only the apps where you want a breathing pause. You can change this list any time.",
        "Choose how long the breathing pause lasts before opening selected apps.",
        "Choose when pauses repeat and how long you can leave an app before they appear again."
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("${step + 1} of ${titles.size}: ${titles[step]}") },
        text = { Text(descriptions[step]) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (step == titles.lastIndex) "Done" else "Next")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip") }
                if (step > 0) androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            }
        }
    )
}

@Composable
private fun HowItWorksDialog(
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How Mora works",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HowItWorksItem(
                    heading = "1. Tasks & due dates",
                    body = "Add tasks with a due date and time. Tasks finished at or before their due time count as completed on time; tasks finished after their due time count as completed late."
                )
                HowItWorksItem(
                    heading = "2. Reminders",
                    body = "Deadline reminders send periodic check-ins for upcoming and past-due tasks. The optional persistent status notification shows your next due task in the notification shade."
                )
                HowItWorksItem(
                    heading = "3. Progress & streaks",
                    body = "On-time rate measures on-time completions against late completions and currently overdue tasks once you complete your first task. Consecutive-day streaks and completion history are preserved even if you delete old tasks."
                )
                HowItWorksItem(
                    heading = "4. Screen time",
                    body = "When Track app usage is enabled with Usage Access, Progress shows today's top foreground app durations stored locally on your phone."
                )
                HowItWorksItem(
                    heading = "5. Pause before apps & Focus lock",
                    body = "Pause before apps shows a calm breathing screen for 8-20 seconds when you open selected apps, with an optional return grace window and repeat timer. Focus lock shows a reminder only when a Focus lock task is overdue."
                )
                HowItWorksItem(
                    heading = "6. Permissions & battery tips",
                    body = "Every permission is optional and requested only when you turn on a feature that needs it. Background reliability in Settings shows your battery status and opens Android's battery controls if reminders or app pauses are delayed."
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun HowItWorksItem(
    heading: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PauseStatusBadge(status: PauseFeatureStatus) {
    val (label, containerColor, contentColor) = when (status) {
        PauseFeatureStatus.ON -> Triple(
            "On",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary
        )
        PauseFeatureStatus.NEEDS_PERMISSION -> Triple(
            "Needs permission",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        PauseFeatureStatus.OFF -> Triple(
            "Off",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val switchScale = remember { Animatable(1f) }
    var hasAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (hasAnimated) {
            switchScale.animateTo(1.06f, tween(90))
            switchScale.animateTo(1f, tween(150))
        } else {
            hasAnimated = true
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer {
                scaleX = switchScale.value
                scaleY = switchScale.value
            }
        )
    }
}

@Composable
private fun TaskHiddenDeletionRow(
    task: Task,
    onDeleteConfirmed: () -> Unit
) {
    val deadlineText = remember(task.deadlineTimestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(task.deadlineTimestamp))
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Due $deadlineText - ${if (task.status == TaskStatus.COMPLETED) "Done" else "Pending"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HoldToDeleteButton(
                taskTitle = task.title,
                onDeleteConfirmed = onDeleteConfirmed
            )
        }
    }
}

@Composable
private fun HoldToDeleteButton(
    taskTitle: String,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = remember(context) { MoraHaptics(context) }
    val progress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val job = launch {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
                )
            }
            val hapticJob = launch {
                while (isHolding && progress.value < 1f) {
                    haptics.onBreathingFrame(progress.value, true)
                    delay(300L)
                }
            }
            job.join()
            hapticJob.cancel()
            if (progress.value >= 0.99f) {
                haptics.onBreathingCycleComplete()
                onDeleteConfirmed()
            }
            isHolding = false
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = modifier
                .size(46.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            tryAwaitRelease()
                            isHolding = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                strokeWidth = 3.dp
            )
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Hold 5 seconds to delete $taskTitle",
                tint = if (isHolding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isHolding) "${((1f - progress.value) * 5).toInt() + 1}s" else "Hold 5s",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isHolding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
