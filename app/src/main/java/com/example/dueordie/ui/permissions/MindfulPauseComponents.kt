package com.example.dueordie.ui.permissions

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.MoraHaptics
import com.example.dueordie.util.PausePing
import com.example.dueordie.util.PermissionHelper
import com.example.dueordie.util.SelectableAppInfo
import com.example.dueordie.util.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.withContext

@Composable
fun MindfulPausePreviewDialog(
    durationSeconds: Int,
    promptText: String,
    playPingOnStart: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember(context) { MoraHaptics(context) }
    val reducedMotion = remember { PermissionHelper.isReducedMotionEnabled(context) }
    val cycleCount = OnboardingPreferences.durationSecondsToCycles(durationSeconds).coerceIn(1, 4)
    val effectiveDurationSeconds = OnboardingPreferences.cyclesToDurationSeconds(cycleCount)
    val cycleDurationMs = (effectiveDurationSeconds * 1000L) / cycleCount
    val inhaleMs = (cycleDurationMs * 0.46f).toLong().toInt()
    val holdMs = (cycleDurationMs * 0.04f).toLong().toInt()
    val exhaleMs = (cycleDurationMs * 0.46f).toLong().toInt()
    val restMs = (cycleDurationMs * 0.04f).toLong().toInt()

    val circleScale = remember { Animatable(0.75f) }
    var currentCycle by remember(durationSeconds) { mutableIntStateOf(1) }
    var phaseText by remember(durationSeconds) { mutableStateOf("Breathe in") }
    var isExhaling by remember(durationSeconds) { mutableStateOf(false) }
    var isBreathingComplete by remember(durationSeconds) { mutableStateOf(false) }

    LaunchedEffect(durationSeconds, playPingOnStart) {
        if (playPingOnStart) PausePing.play(context)
        isBreathingComplete = false
        phaseText = "Breathe in"
        currentCycle = 1
        isExhaling = false
        circleScale.snapTo(0.75f)

        for (cycle in 1..cycleCount) {
            currentCycle = cycle

            // 1. Inhale
            phaseText = "Breathe in"
            isExhaling = false
            if (reducedMotion) {
                delay(inhaleMs.toLong())
            } else {
                circleScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = inhaleMs,
                        easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
                    )
                ) {
                    val p = ((value - 0.75f) / 0.25f).coerceIn(0f, 1f)
                    haptics.onBreathingFrame(p, isInhale = true)
                }
            }

            if (holdMs > 0) delay(holdMs.toLong())

            // 2. Exhale
            phaseText = "Breathe out"
            isExhaling = true
            if (reducedMotion) {
                delay(exhaleMs.toLong())
            } else {
                circleScale.animateTo(
                    targetValue = 0.75f,
                    animationSpec = tween(
                        durationMillis = exhaleMs,
                        easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
                    )
                ) {
                    val p = ((1f - value) / 0.25f).coerceIn(0f, 1f)
                    haptics.onBreathingFrame(p, isInhale = false)
                }
            }

            if (restMs > 0) delay(restMs.toLong())
        }

        phaseText = "Ready"
        isBreathingComplete = true
        haptics.onBreathingCycleComplete()
    }

    val progress = if (!isExhaling) {
        ((circleScale.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
    } else {
        ((1f - circleScale.value) / 0.25f).coerceIn(0f, 1f)
    }
    val particleColor = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pause preview",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    val effectivePrompt = remember(promptText) {
                        if (promptText.isNotBlank() && promptText != OnboardingPreferences.DEFAULT_PAUSE_PROMPT) {
                            promptText
                        } else {
                            com.example.dueordie.util.MindfulPrompts.getRandomPrompt()
                        }
                    }

                    if (effectivePrompt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = effectivePrompt,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = if (isBreathingComplete) {
                                    "$cycleCount breathing cycle${if (cycleCount > 1) "s" else ""} complete. Choose Continue or dismiss."
                                } else {
                                    "Cycle $currentCycle of $cycleCount. $phaseText"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!reducedMotion && !isBreathingComplete) {
                            Canvas(Modifier.fillMaxSize().zIndex(1f)) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val outerRadius = size.minDimension * 0.43f
                                repeat(10) { index ->
                                    val angle = (2.0 * PI * index / 10.0) + (progress * 0.12 * if (!isExhaling) -1 else 1)
                                    val radius = outerRadius * if (!isExhaling) {
                                        0.98f - 0.36f * progress
                                    } else {
                                        0.62f + 0.36f * progress
                                    }
                                    drawCircle(
                                        color = particleColor.copy(alpha = 0.25f + (index % 3) * 0.08f),
                                        radius = (2.0f + (index % 3) * 0.7f) * density,
                                        center = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(152.dp)
                                .graphicsLayer {
                                    scaleX = circleScale.value
                                    scaleY = circleScale.value
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = phaseText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isBreathingComplete) "Choose below" else "Cycle $currentCycle of $cycleCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    AnimatedVisibility(
                        visible = isBreathingComplete,
                        modifier = Modifier.heightIn(min = 52.dp),
                        enter = fadeIn(tween(650))
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.wrapContentWidth().widthIn(min = 140.dp).heightIn(min = 48.dp)
                        ) {
                            Text("Continue", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.wrapContentWidth().widthIn(min = 140.dp).heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = "App launch detection",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Mora uses Usage Access and Display over other apps permissions to detect when you open selected apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This allows Mora to present a calming breathing pause before you open apps you choose to monitor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Mora never reads screen text, messages, passwords, or personal content. All detection happens on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Continue to settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Not now")
            }
        }
    )
}

@Composable
fun PausePromptDialog(
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialPrompt) { mutableStateOf(initialPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pause prompt",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose a preset phrase or write your own 1-line note:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OnboardingPreferences.PAUSE_PROMPT_PRESETS.forEach { preset ->
                    val label = if (preset.isEmpty()) "No phrase" else preset
                    val isSelected = text.trim() == preset
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { text = preset }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 80) text = it.replace("\n", " ") },
                    singleLine = true,
                    placeholder = { Text("Custom 1-line prompt (or leave blank)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { text = OnboardingPreferences.DEFAULT_PAUSE_PROMPT },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Reset default")
                    }
                    Text(
                        text = "${text.length}/80",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(text.trim())
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SelectPauseAppsDialog(
    initialSelectedPackages: Set<String>,
    initialDisabledReInterventionPackages: Set<String>,
    isReInterventionEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (selectedPackages: Set<String>, disabledReInterventionPackages: Set<String>) -> Unit
) {
    val context = LocalContext.current
    val onboardingPrefs = remember { OnboardingPreferences(context) }
    var showPickerTip by remember {
        mutableStateOf(onboardingPrefs.showTutorialHints && !onboardingPrefs.hasSeenAppPickerTip)
    }
    var selectedPackages by remember { mutableStateOf(initialSelectedPackages) }
    var disabledReInterventionPackages by remember { mutableStateOf(initialDisabledReInterventionPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<SelectableAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        loadError = null
        runCatching {
            withContext(Dispatchers.IO) {
                UsageStatsHelper.getInstalledUserApps(context, initialSelectedPackages)
            }
        }.onSuccess { loaded ->
            allApps = loaded
            isLoading = false
        }.onFailure {
            loadError = "Couldn't load installed apps."
            isLoading = false
        }
    }

    val filteredApps = remember(allApps, searchQuery, selectedPackages) {
        allApps
            .filter {
                searchQuery.isBlank() ||
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<SelectableAppInfo> { selectedPackages.contains(it.packageName) }
                    .thenBy { it.appName.lowercase() }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Choose apps to pause",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${selectedPackages.size} selected. Take two slow breaths before opening these apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showPickerTip) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tip: Start with 1-3 apps you open on autopilot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onboardingPrefs.hasSeenAppPickerTip = true
                                    showPickerTip = false
                                }
                            ) {
                                Text("Got it")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    loadError != null -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = loadError ?: "Couldn't load installed apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = { reloadTrigger++ }) {
                                Text("Retry")
                            }
                        }
                    }
                    filteredApps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "No matching apps for \"$searchQuery\""
                                } else {
                                    "No launchable apps found"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(
                                items = filteredApps,
                                key = { it.packageName }
                            ) { app ->
                                val isSelected = selectedPackages.contains(app.packageName)
                                val showAgainForApp = !disabledReInterventionPackages.contains(app.packageName)
                                val iconBitmap = remember(app.packageName, app.icon) {
                                    try {
                                        app.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPackages = if (isSelected) {
                                                selectedPackages - app.packageName
                                            } else {
                                                selectedPackages + app.packageName
                                            }
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap,
                                                contentDescription = null,
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

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Switch(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedPackages = if (checked) {
                                                    selectedPackages + app.packageName
                                                } else {
                                                    selectedPackages - app.packageName
                                                }
                                            }
                                        )
                                    }

                                    if (isSelected && isReInterventionEnabled) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 48.dp, top = 4.dp, end = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Show again while staying in ${app.appName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Switch(
                                                checked = showAgainForApp,
                                                onCheckedChange = { enabled ->
                                                    disabledReInterventionPackages = if (enabled) {
                                                        disabledReInterventionPackages - app.packageName
                                                    } else {
                                                        disabledReInterventionPackages + app.packageName
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onboardingPrefs.hasSeenAppPickerTip = true
                            onSave(selectedPackages, disabledReInterventionPackages)
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
