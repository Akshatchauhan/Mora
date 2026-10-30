package com.example.dueordie.ui.permissions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.dueordie.util.MoraHaptics
import com.example.dueordie.util.OnboardingPreferences
import com.example.dueordie.util.PausePing
import com.example.dueordie.util.PermissionHelper
import com.microsoft.clarity.modifiers.clarityMask
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DynamicPauseStage {
    STAGE_1_REFLECTION, // Initial calm reflection phase: shows app, usage, and prompt
    STAGE_2_BREATHING,  // Organic liquid breathing mandala with escalating graphics
    STAGE_3_DECISION    // Conscious choice: Step away vs Continue
}

@Composable
fun DynamicMindfulPauseContent(
    targetPackage: String,
    promptText: String,
    durationSeconds: Int = 10,
    isStrictDailyLimitReached: Boolean = false,
    dailyScreenTimeMinutes: Long = 0L,
    dailyScreenTimeLimitMinutes: Int = 30,
    onContinueToApp: () -> Unit,
    onStepAwayToTasks: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember(context) { MoraHaptics(context) }
    val reducedMotion = remember { PermissionHelper.isReducedMotionEnabled(context) }

    // App info resolution
    val pm = context.packageManager
    val isPreview = targetPackage.isBlank() || targetPackage == "preview"
    val appLabel = remember(targetPackage) {
        if (isPreview) {
            "Instagram"
        } else {
            runCatching {
                val info = pm.getApplicationInfo(targetPackage, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrDefault(targetPackage)
        }
    }
    val appIcon = remember(targetPackage) {
        if (isPreview) {
            null
        } else {
            runCatching {
                val drawable = pm.getApplicationIcon(targetPackage)
                drawable.toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    val effectiveScreenTimeMin = if (isPreview) 22L else dailyScreenTimeMinutes
    val effectiveLimitMin = if (isPreview) 30 else dailyScreenTimeLimitMinutes

    val effectiveDurationSeconds = durationSeconds.coerceIn(
        OnboardingPreferences.MIN_PAUSE_DURATION_SECONDS,
        OnboardingPreferences.MAX_PAUSE_DURATION_SECONDS
    )
    val cycleCount = OnboardingPreferences.durationSecondsToCycles(effectiveDurationSeconds).coerceIn(1, 4)
    val cycleDurationMs = (effectiveDurationSeconds * 1000L) / cycleCount
    val inhaleMs = (cycleDurationMs * 0.44f).toInt().coerceAtLeast(1400)
    val holdMs = (cycleDurationMs * 0.08f).toInt().coerceAtLeast(200)
    val exhaleMs = (cycleDurationMs * 0.42f).toInt().coerceAtLeast(1400)
    val restMs = (cycleDurationMs * 0.06f).toInt().coerceAtLeast(200)

    var currentStage by remember { mutableStateOf(DynamicPauseStage.STAGE_1_REFLECTION) }
    var currentCycle by remember { mutableIntStateOf(1) }
    var phaseText by remember { mutableStateOf("Breathe in") }

    val breathingExpansion = remember { Animatable(0f) }
    val rotationDrift = remember { Animatable(0f) }

    // Ambient bottom ribbon shimmer (Gemini Assistant aesthetic)
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiCaustics")
    val ambientShimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CausticShimmer"
    )

    // Master Stage Orchestrator
    LaunchedEffect(Unit) {
        haptics.resetBreathCycle()
        PausePing.playIfEnabled(context)

        // STAGE 1: Reflection & Context
        // Give the user a calm 2.0 seconds to absorb the phrase and app context
        delay(2000L)

        // STAGE 2: Escalating fluid breathing cycles
        currentStage = DynamicPauseStage.STAGE_2_BREATHING

        for (cycle in 1..cycleCount) {
            currentCycle = cycle

            // 1. Inhale
            phaseText = "Breathe in"
            launch {
                rotationDrift.animateTo(
                    targetValue = rotationDrift.value + 36f,
                    animationSpec = tween(inhaleMs, easing = FastOutSlowInEasing)
                )
            }
            if (reducedMotion) {
                delay(inhaleMs.toLong())
            } else {
                breathingExpansion.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(inhaleMs, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))
                ) {
                    haptics.onBreathingFrame(value, isInhale = true)
                }
            }

            // 2. Hold at top of breath
            phaseText = "Hold"
            delay(holdMs.toLong())

            // 3. Exhale
            phaseText = "Breathe out"
            launch {
                rotationDrift.animateTo(
                    targetValue = rotationDrift.value + 12f,
                    animationSpec = tween(exhaleMs, easing = FastOutSlowInEasing)
                )
            }
            if (reducedMotion) {
                delay(exhaleMs.toLong())
            } else {
                breathingExpansion.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(exhaleMs, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))
                ) {
                    haptics.onBreathingFrame(1f - value, isInhale = false)
                }
            }

            // 4. Rest at bottom of breath
            phaseText = "Rest"
            delay(restMs.toLong())
        }

        // STAGE 3: Conscious Choice (Fork in the Road)
        haptics.onBreathingCycleComplete()
        currentStage = DynamicPauseStage.STAGE_3_DECISION
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Bottom Ambient Wave Ribbon (Gemini Assistant Style Glow)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = 0.65f + (ambientShimmer * 0.35f) }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f + 0.05f * ambientShimmer),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + 0.10f * ambientShimmer)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Header & Usage Badge Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = appLabel,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Spa,
                                contentDescription = "Mora",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$effectiveScreenTimeMin m today / $effectiveLimitMin m limit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Close / dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Mindfulness Phrase
            val effectivePhrase = remember(promptText) {
                if (promptText.isNotBlank() && promptText != OnboardingPreferences.DEFAULT_PAUSE_PROMPT) {
                    promptText
                } else {
                    "Take a mindful moment before diving in."
                }
            }

            Text(
                text = effectivePhrase,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 32.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clarityMask()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stage 1: Preparation indicator
            if (currentStage == DynamicPauseStage.STAGE_1_REFLECTION) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelfImprovement,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Preparing your breathing pause...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center: Breathing Mandala Canvas OR Completion Mandala
            val primaryColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sacred Mandala Graphics & Starlight Particles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.45f
                    val expansion = if (currentStage == DynamicPauseStage.STAGE_3_DECISION) 0.5f else breathingExpansion.value
                    val rot = rotationDrift.value

                    // Escalating resonance wave ring in cycle 2+
                    if (currentCycle > 1 || currentStage == DynamicPauseStage.STAGE_3_DECISION) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.18f * expansion),
                            radius = baseRadius * (0.85f + 0.35f * expansion),
                            center = center
                        )
                    }

                    // 6 Overlapping Translucent Petals with soft blend
                    val petalCount = 6
                    val currentPetalRadius = baseRadius * (0.38f + 0.52f * expansion)
                    val petalOffsetDist = baseRadius * 0.36f * expansion

                    for (i in 0 until petalCount) {
                        val angleRad = (i * 2.0 * PI / petalCount) + Math.toRadians(rot.toDouble())
                        val petalCenter = Offset(
                            x = (center.x + cos(angleRad) * petalOffsetDist).toFloat(),
                            y = (center.y + sin(angleRad) * petalOffsetDist).toFloat()
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.42f + (0.15f * expansion)),
                                    primaryColor.copy(alpha = 0.15f * expansion),
                                    Color.Transparent
                                ),
                                center = petalCenter,
                                radius = currentPetalRadius
                            ),
                            radius = currentPetalRadius,
                            center = petalCenter,
                            blendMode = BlendMode.Screen
                        )
                    }

                    // Inward floating starlight particles
                    val particleCount = 14
                    for (k in 0 until particleCount) {
                        val pAngle = (k * 2.0 * PI / particleCount) + (rot * 0.015f)
                        val pDist = baseRadius * (0.92f - 0.48f * expansion)
                        val pCenter = Offset(
                            x = (center.x + cos(pAngle) * pDist).toFloat(),
                            y = (center.y + sin(pAngle) * pDist).toFloat()
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.35f + (k % 3) * 0.18f),
                            radius = (2.2f + (k % 3) * 0.8f) * density,
                            center = pCenter
                        )
                    }

                    // Central Luminous Core Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.90f),
                                primaryColor.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 0.42f * (0.6f + 0.4f * expansion)
                        ),
                        radius = baseRadius * 0.42f * (0.6f + 0.4f * expansion),
                        center = center
                    )
                }

                // Dynamic Phase Typography inside / over the mandala
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (currentStage == DynamicPauseStage.STAGE_3_DECISION) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Complete",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mindful",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = phaseText,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cycle $currentCycle of $cycleCount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Stage 3: Decision - Fork in the Road
            AnimatedVisibility(
                visible = currentStage == DynamicPauseStage.STAGE_3_DECISION,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400) { it / 3 }),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Where do you want your focus to go?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Hero Button: Step away & return to Tasks
                    Button(
                        onClick = onStepAwayToTasks,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Step away & return to Tasks",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Secondary Choice: Continue to App OR Daily Cap Locked Shield
                    if (isStrictDailyLimitReached) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Daily limit reached ($effectiveLimitMin m / $effectiveLimitMin m)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "In Strict Mode, chosen apps remain locked until midnight.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onContinueToApp,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Continue to $appLabel",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
