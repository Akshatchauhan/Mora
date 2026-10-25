package com.example.dueordie.ui.task

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Velocity
import com.example.dueordie.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.model.UrgencyLevel
import com.example.dueordie.ui.theme.SemanticSuccessGreen
import com.example.dueordie.ui.theme.SemanticWarningAmber
import com.example.dueordie.util.MoraHaptics
import com.example.dueordie.util.TimeFormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TaskHomeScreen(
    onNavigateToGuiltTrip: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = viewModel(
        factory = TaskViewModel.Factory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val onboardingPrefs = remember { com.example.dueordie.util.OnboardingPreferences(context) }
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showTour by remember {
        mutableStateOf(onboardingPrefs.showTutorialHints && !onboardingPrefs.hasCompletedTasksTour)
    }
    var tourStep by remember {
        mutableStateOf(onboardingPrefs.tasksTourStep)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkOverdueTasks()
                showTour = onboardingPrefs.showTutorialHints && !onboardingPrefs.hasCompletedTasksTour
                tourStep = onboardingPrefs.tasksTourStep
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // In-App Notification Banner state (replaces OS snackbar)
    var inAppBannerMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            inAppBannerMessage = msg
            delay(3500L)
            inAppBannerMessage = null
            viewModel.clearSnackbar()
        }
    }

    // Localized clock tick once every 15 seconds for visible countdown labels
    val hasActiveTasks = uiState.pendingCount > 0 || uiState.overdueCount > 0
    val liveNowMs by produceState(initialValue = System.currentTimeMillis(), key1 = hasActiveTasks) {
        while (hasActiveTasks) {
            value = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val hasExampleTasks = remember(uiState.allTasks) {
        uiState.allTasks.any { it.title.startsWith(TaskViewModel.EXAMPLE_PREFIX) }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Snapchat-style pull down to sync with Notion (only active when Notion integration is configured)
    val isNotionConnected = uiState.isNotionConnected
    val isNotionSyncing = uiState.isNotionSyncing
    val density = LocalDensity.current
    val pullThresholdPx = with(density) { 90.dp.toPx() }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var hasFiredPullHaptic by remember { mutableStateOf(false) }

    val animatedPullOffset by animateFloatAsState(
        targetValue = if (isNotionSyncing) pullThresholdPx else pullDistance,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "notionPull"
    )

    val notionPullScrollConnection = remember(isNotionConnected, isNotionSyncing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isNotionConnected || isNotionSyncing) return Offset.Zero
                if (available.y < 0 && pullDistance > 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistance)
                    pullDistance += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isNotionConnected || isNotionSyncing) return Offset.Zero
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    val newDist = pullDistance + available.y * 0.45f
                    pullDistance = newDist.coerceIn(0f, pullThresholdPx * 1.4f)
                    if (pullDistance >= pullThresholdPx && !hasFiredPullHaptic) {
                        hasFiredPullHaptic = true
                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(18, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else if (pullDistance < pullThresholdPx) {
                        hasFiredPullHaptic = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isNotionConnected && pullDistance >= pullThresholdPx && !isNotionSyncing) {
                    pullDistance = 0f
                    hasFiredPullHaptic = false
                    viewModel.syncNotion()
                    return Velocity.Zero
                }
                pullDistance = 0f
                hasFiredPullHaptic = false
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .nestedScroll(notionPullScrollConnection)
                .offset { IntOffset(0, (animatedPullOffset * 0.35f).toInt()) },
            contentPadding = PaddingValues(bottom = navBarBottom + 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: "Tasks" title on left, Settings gear on right
            item(key = "tasks-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = statusBarTop + 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tasks",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onNavigateToPermissions,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_settings),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Animated Filter Row (no numbers, sliding active pill, late dot indicator)
            if (uiState.allTasks.isNotEmpty()) {
                item(key = "tasks-filters") {
                    TaskFilterRow(
                        selectedFilter = uiState.selectedFilter,
                        overdueCount = uiState.overdueCount,
                        onFilterSelected = { viewModel.setFilter(it) }
                    )
                }
            }

            if (showTour) {
                item(key = "tasks-tour") {
                    TasksTourCard(
                        step = tourStep,
                        onNext = {
                            if (tourStep < 2) {
                                tourStep += 1
                                onboardingPrefs.tasksTourStep = tourStep
                            } else {
                                onboardingPrefs.hasCompletedTasksTour = true
                                showTour = false
                            }
                        },
                        onSkip = {
                            onboardingPrefs.hasCompletedTasksTour = true
                            showTour = false
                        },
                        onActionClick = {
                            when (tourStep) {
                                0 -> viewModel.openAddTaskDialog()
                                1 -> viewModel.setFilter(TaskFilter.PENDING)
                                2 -> {
                                    onboardingPrefs.hasCompletedTasksTour = true
                                    showTour = false
                                    onNavigateToPermissions()
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (hasExampleTasks) {
                item(key = "example-tasks-banner") {
                    ExampleTasksBanner(
                        onRemoveExamples = { viewModel.removeExampleTasks() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (uiState.overdueCount > 0 && uiState.selectedFilter != TaskFilter.OVERDUE) {
                item(key = "overdue-banner") {
                    OverdueNoticeBanner(
                        overdueCount = uiState.overdueCount,
                        onShowOverdue = { viewModel.setFilter(TaskFilter.OVERDUE) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (uiState.filteredTasks.isEmpty()) {
                item(key = "empty-state") {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 380.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyTasksState(
                            filter = uiState.selectedFilter,
                            onNewTaskClick = { viewModel.openAddTaskDialog() },
                            onAddExamplesClick = if (uiState.allTasks.isEmpty()) {
                                { viewModel.addExampleTasks() }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                items(items = uiState.filteredTasks, key = { it.id }) { task ->
                    SwipeableTaskCard(
                        task = task,
                        currentTimeMs = liveNowMs,
                        onToggleStatus = { viewModel.toggleTaskStatus(task) },
                        onEditClick = { viewModel.openEditTaskDialog(task) },
                        onDeleteTask = { viewModel.deleteTask(task) },
                        isDeleteDisabled = onboardingPrefs.isHiddenTaskDeletionEnabled
                    )
                }
            }
        }

        // Snapchat-style pull down indicator at top of screen
        if (isNotionConnected) {
            SnapchatNotionPullIndicator(
                offsetPx = animatedPullOffset,
                maxOffsetPx = pullThresholdPx,
                isSyncing = isNotionSyncing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarTop + 8.dp)
            )
        }

        // Custom in-app floating pill notification banner positioned right above bottom navbar
        MoraInAppNotificationBanner(
            message = inAppBannerMessage,
            canUndo = uiState.canUndoLastAction,
            onUndo = {
                viewModel.undoLastAction()
                inAppBannerMessage = null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        )


    }

    if (uiState.isTaskDialogVisible) {
        TaskDialog(
            editingTask = uiState.editingTask,
            onDismiss = { viewModel.dismissTaskDialog() },
            onSave = { title, desc, deadline, priority, requirePhoto, hostageMode, photoPath ->
                viewModel.saveTask(
                    title = title,
                    description = desc,
                    deadlineTimestamp = deadline,
                    priority = priority,
                    requirePhotoProof = requirePhoto,
                    isHostageModeEnabled = hostageMode,
                    photoPath = photoPath
                )
            }
        )
    }
}

/**
 * Animated Segmented Filter Row with sliding indicator pill and red dot on Late.
 */
@Composable
private fun TaskFilterRow(
    selectedFilter: TaskFilter,
    overdueCount: Int,
    onFilterSelected: (TaskFilter) -> Unit
) {
    val filters = remember { TaskFilter.entries }
    val selectedIndex = filters.indexOf(selectedFilter).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "filterIndicatorOffset"
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            val tabWidth = maxWidth / filters.size

            // Sliding active indicator pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .offset {
                        IntOffset(
                            x = (animatedIndex * tabWidth.toPx()).toInt(),
                            y = 0
                        )
                    }
            ) {}

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val isLateWithOverdue = filter == TaskFilter.OVERDUE && overdueCount > 0

                    val textColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isLateWithOverdue -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "filterTextColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onFilterSelected(filter) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected || isLateWithOverdue) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = textColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipeable Task Card:
 * - Swipe right to edit
 * - Swipe left reveals red "Hold to delete" button
 * - Tap and hold anywhere on the whole card to mark complete
 */
@Composable
private fun SnapchatNotionPullIndicator(
    offsetPx: Float,
    maxOffsetPx: Float,
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    if (offsetPx <= 4f && !isSyncing) return

    val progress = (offsetPx / maxOffsetPx).coerceIn(0f, 1f)
    val scale = (0.65f + progress * 0.35f).coerceIn(0.65f, 1f)
    val alpha = (progress * 1.5f).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "notionSpin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "spin"
    )

    val iconRotation = if (isSyncing) spinRotation else (progress * 360f)

    Box(
        modifier = modifier
            .offset { IntOffset(0, (offsetPx * 0.5f).toInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = if (isSyncing) 1f else alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notion),
                        contentDescription = "Notion",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = iconRotation }
                    )
                }

                Text(
                    text = when {
                        isSyncing -> "Syncing Notion..."
                        progress >= 1f -> "Release to sync Notion"
                        else -> "Pull to sync Notion"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Swipeable Task Card:
 * - Swipe right to edit
 * - Swipe left reveals red "Hold to delete" button (when task deletion is enabled on task page)
 * - Tap and hold anywhere on the stationary card to mark complete
 * - Tapping NEVER opens edit dialog
 */
@Composable
private fun SwipeableTaskCard(
    task: Task,
    currentTimeMs: Long,
    onToggleStatus: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteTask: () -> Unit,
    isDeleteDisabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = remember(context) { MoraHaptics(context) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    var isDeleteRevealed by remember { mutableStateOf(false) }

    val swipeRevealPx = with(density) { 96.dp.toPx() }
    val swipeEditThresholdPx = with(density) { 76.dp.toPx() }

    var holdProgress by remember(task.id) { mutableFloatStateOf(0f) }
    val isCompleted = task.status == TaskStatus.COMPLETED
    val isOverdue = !isCompleted && task.isOverdueAt(currentTimeMs)
    val holdDuration = if (isOverdue) 2_400L else 1_400L

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Revealed Background Actions
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit on left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Hold to delete button on right (only shown when deletion is allowed on task page)
            if (!isDeleteDisabled) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(96.dp)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    SwipeHoldToDeleteButton(
                        onDelete = {
                            coroutineScope.launch {
                                offsetX.snapTo(0f)
                                isDeleteRevealed = false
                            }
                            onDeleteTask()
                        }
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
        }

        // Foreground Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(task.id, isCompleted, isDeleteDisabled) {
                    val touchSlop = viewConfiguration.touchSlop
                    val movementThreshold = with(density) { 6.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        val startedAt = SystemClock.elapsedRealtime()
                        var isDragging = false
                        var isHoldCompleted = false

                        val holdJob = coroutineScope.launch {
                            delay(280L) // Stationary deadzone so swiping or tapping never starts hold timer
                            if (isDragging) return@launch
                            val holdStart = SystemClock.elapsedRealtime()
                            while (isActive && !isDragging) {
                                val elapsed = SystemClock.elapsedRealtime() - holdStart
                                holdProgress = (elapsed.toFloat() / (holdDuration - 280L)).coerceIn(0f, 1f)
                                haptics.onHoldToCompleteProgress(holdProgress)
                                if (elapsed >= (holdDuration - 280L)) {
                                    isHoldCompleted = true
                                    holdProgress = 1f
                                    haptics.onHoldTaskCompleted()
                                    onToggleStatus()
                                    break
                                }
                                delay(16L)
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!pointer.pressed) {
                                // Finger lifted
                                holdJob.cancel()
                                if (isDragging) {
                                    coroutineScope.launch {
                                        if (offsetX.value > swipeEditThresholdPx) {
                                            offsetX.animateTo(0f)
                                            isDeleteRevealed = false
                                            onEditClick()
                                        } else if (!isDeleteDisabled && offsetX.value < -swipeRevealPx / 2f) {
                                            offsetX.animateTo(-swipeRevealPx)
                                            isDeleteRevealed = true
                                        } else {
                                            offsetX.animateTo(0f)
                                            isDeleteRevealed = false
                                        }
                                    }
                                } else if (!isHoldCompleted) {
                                    val tapDuration = SystemClock.elapsedRealtime() - startedAt
                                    if (tapDuration < 450L) {
                                        if (isDeleteRevealed) {
                                            coroutineScope.launch {
                                                offsetX.animateTo(0f)
                                                isDeleteRevealed = false
                                            }
                                        }
                                        // Tapping does NOT open task edit page!
                                    }
                                }
                                if (holdProgress > 0.1f && !isHoldCompleted) {
                                    haptics.onHoldCancelled()
                                }
                                holdProgress = 0f
                                break
                            }

                            val deltaX = pointer.position.x - startPos.x
                            val deltaY = pointer.position.y - startPos.y
                            val totalDistance = kotlin.math.hypot(deltaX, deltaY)

                            // Cancel hold timer immediately upon ANY slight movement
                            if (totalDistance > movementThreshold && !isHoldCompleted) {
                                holdJob.cancel()
                                if (holdProgress > 0f) {
                                    holdProgress = 0f
                                    haptics.onHoldCancelled()
                                }
                            }

                            if (!isDragging) {
                                if (kotlin.math.abs(deltaX) > touchSlop) {
                                    // Horizontal swipe detected
                                    isDragging = true
                                    holdJob.cancel()
                                    holdProgress = 0f
                                    pointer.consume()
                                } else if (kotlin.math.abs(deltaY) > touchSlop) {
                                    // Vertical scroll detected
                                    holdJob.cancel()
                                    holdProgress = 0f
                                    break
                                }
                            } else {
                                pointer.consume()
                                val baseOffset = if (isDeleteRevealed) -swipeRevealPx else 0f
                                val minOffset = if (isDeleteDisabled) 0f else -swipeRevealPx * 1.25f
                                val newOffset = (baseOffset + deltaX)
                                    .coerceIn(minOffset, swipeEditThresholdPx * 1.25f)
                                coroutineScope.launch {
                                    offsetX.snapTo(newOffset)
                                }
                            }
                        }
                    }
                }
        ) {
            TaskItemCardContent(
                task = task,
                currentTimeMs = currentTimeMs,
                holdProgress = holdProgress,
                onToggleStatus = onToggleStatus
            )
        }
    }
}

/**
 * Hold to Delete circular button inside the revealed swipe area.
 */
@Composable
private fun SwipeHoldToDeleteButton(
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember(context) { MoraHaptics(context) }
    val gestureScope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val holdDuration = 1_400L

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startedAt = SystemClock.elapsedRealtime()
                    val ticker = gestureScope.launch {
                        while (SystemClock.elapsedRealtime() - startedAt < holdDuration) {
                            progress = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / holdDuration).coerceIn(0f, 1f)
                            haptics.onHoldToCompleteProgress(progress)
                            delay(16L)
                        }
                    }
                    var stillPressed = true
                    while (stillPressed) {
                        val event = awaitPointerEvent()
                        stillPressed = event.changes.any { it.pressed }
                        event.changes.forEach { it.consume() }
                    }
                    ticker.cancel()
                    if (SystemClock.elapsedRealtime() - startedAt >= holdDuration) {
                        progress = 1f
                        haptics.onHoldTaskCompleted()
                        onDelete()
                    } else {
                        haptics.onHoldCancelled()
                    }
                    progress = 0f
                }
            }
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(38.dp),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp
                )
            }
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Hold to delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Hold 2s",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun TaskItemCardContent(
    task: Task,
    currentTimeMs: Long,
    holdProgress: Float,
    onToggleStatus: () -> Unit
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val urgency = task.calculateUrgencyLevel(currentTimeMs)
    val isOverdue = !isCompleted && task.isOverdueAt(currentTimeMs)

    val statusColor = when {
        isCompleted -> SemanticSuccessGreen
        isOverdue -> MaterialTheme.colorScheme.error
        urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.HIGH -> SemanticWarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val statusLabel = when {
        isCompleted && task.isCompletedOnTime -> "Completed on time"
        isCompleted -> "Completed late"
        isOverdue -> "Late"
        urgency == UrgencyLevel.CRITICAL -> "Due within 3h"
        urgency == UrgencyLevel.HIGH -> "Due today"
        else -> null
    }

    val isHighPriorityActive = !isCompleted && task.priority == com.example.dueordie.data.model.TaskPriority.HIGH
    val borderStroke = BorderStroke(
        width = if (holdProgress > 0f) 1.5.dp else if (isHighPriorityActive) 1.5.dp else 1.dp,
        color = when {
            holdProgress > 0f -> MaterialTheme.colorScheme.primary
            isOverdue -> MaterialTheme.colorScheme.error
            isHighPriorityActive -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outline
        }
    )

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = borderStroke,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox squircle button
            HoldToCompleteCheckbox(
                overdue = isOverdue,
                completed = isCompleted,
                externalProgress = holdProgress,
                onClick = onToggleStatus
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (pColor, pBg) = when (task.priority) {
                            com.example.dueordie.data.model.TaskPriority.HIGH -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            com.example.dueordie.data.model.TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            com.example.dueordie.data.model.TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        }

                        Surface(
                            color = pBg,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = task.priority.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = pColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                            )
                        }

                        if (statusLabel != null) {
                            Surface(
                                color = statusColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = if (isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(14.dp)
                    )

                    val timeSummary = if (isCompleted) {
                        "Due ${TimeFormatUtils.formatDeadlineDateTime(task.deadlineTimestamp)}"
                    } else {
                        "${TimeFormatUtils.formatDeadlineDateTime(task.deadlineTimestamp)} (${
                            TimeFormatUtils.formatRemainingCountdown(task.deadlineTimestamp, currentTimeMs)
                        })"
                    }

                    Text(
                        text = timeSummary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (task.requirePhotoProof || !task.photoPath.isNullOrBlank() || task.isHostageModeEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!task.photoPath.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Photo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Photo attached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (task.requirePhotoProof) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Optional photo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (task.isHostageModeEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.LockClock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Focus lock",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (!task.photoPath.isNullOrBlank() && File(task.photoPath).exists()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = File(task.photoPath),
                        contentDescription = "Task photo attachment",
                        modifier = Modifier
                            .height(72.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldToCompleteCheckbox(
    overdue: Boolean,
    completed: Boolean,
    externalProgress: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember(context) { MoraHaptics(context) }
    val checkScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val ringColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    LaunchedEffect(completed) {
        if (completed) {
            checkScale.animateTo(
                targetValue = 1.24f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            checkScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            checkScale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = checkScale.value
                scaleY = checkScale.value
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!completed) {
                        haptics.onHoldTaskCompleted()
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!completed && externalProgress > 0f) {
            CircularProgressIndicator(
                progress = { externalProgress },
                modifier = Modifier.size(36.dp),
                color = ringColor,
                trackColor = ringColor.copy(alpha = 0.15f),
                strokeWidth = 2.5.dp
            )
        }
        Icon(
            imageVector = if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (completed) "Task completed" else "Mark task complete",
            tint = if (completed) SemanticSuccessGreen else ringColor.copy(alpha = 0.85f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun EmptyTasksState(
    filter: TaskFilter,
    onNewTaskClick: () -> Unit,
    onAddExamplesClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (heading, body) = when (filter) {
        TaskFilter.ALL -> "No tasks yet" to "Add a task with a due time to get started."
        TaskFilter.PENDING -> "No upcoming tasks" to "You have no tasks scheduled right now."
        TaskFilter.OVERDUE -> "No late tasks" to "Everything is on schedule."
        TaskFilter.COMPLETED -> "No completed tasks yet" to "Tasks you mark complete will appear here."
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Calm, encouraging checklist icon in soft sage container (replaces Gemini sparkle)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AssignmentTurnedIn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary "+ New task" button positioned right where example button was
            Surface(
                onClick = onNewTaskClick,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New task",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            if (onAddExamplesClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onAddExamplesClick,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "Load example tasks →",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Modern floating in-app notification capsule banner (replaces standard Android OS snackbar).
 */
@Composable
private fun MoraInAppNotificationBanner(
    message: String?,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(220)
        ) + fadeIn(tween(220)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(180)
        ) + fadeOut(tween(180)),
        modifier = modifier
    ) {
        if (message != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF1E2E25),
                border = BorderStroke(1.dp, Color(0x33446050)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = Color(0xFFFAF9F5),
                        modifier = Modifier.weight(1f)
                    )

                    if (canUndo) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            onClick = onUndo,
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF2E6F4E),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "Undo",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksTourCard(
    step: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 14 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 14 })
                },
                label = "tasksTourStep"
            ) { visibleStep ->
                val (title, description, actionLabel) = when (visibleStep) {
                    0 -> Triple(
                        "Your first deadline",
                        "Choose a title and due time. Photo notes and Focus lock are optional.",
                        "Create task"
                    )
                    1 -> Triple(
                        "Keep tasks in view",
                        "Filter by upcoming, late or completed. Finishing tasks builds your history.",
                        "View To do"
                    )
                    else -> Triple(
                        "Optional support",
                        "Progress tracks streaks. Choose apps for a breathing pause in Settings.",
                        "Explore Settings"
                    )
                }
                Column {
                    Text(
                        text = "${visibleStep + 1} of 3: $title",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onSkip,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Skip")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = onActionClick,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(actionLabel)
                            }
                            OutlinedButton(
                                onClick = onNext,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(if (visibleStep < 2) "Next" else "Done")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleTasksBanner(
    onRemoveExamples: () -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Showing example tasks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onRemoveExamples,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Remove examples",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OverdueNoticeBanner(
    overdueCount: Int,
    onShowOverdue: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (overdueCount == 1) {
                    "1 task is past due"
                } else {
                    "$overdueCount tasks are past due"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onShowOverdue) {
                Text(
                    text = "Show late",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
