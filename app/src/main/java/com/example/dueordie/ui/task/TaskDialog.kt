package com.example.dueordie.ui.task

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dueordie.data.model.InteractionModePolicy
import com.example.dueordie.util.OnboardingPreferences
import com.microsoft.clarity.modifiers.clarityMask
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskPriority
import com.example.dueordie.data.model.TaskStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(
    editingTask: Task?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        deadlineTimestamp: Long,
        priority: TaskPriority,
        requirePhotoProof: Boolean,
        isHostageModeEnabled: Boolean,
        photoPath: String?
    ) -> Unit
) {
    val isEditing = editingTask != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(editingTask?.title ?: "") }
    var description by remember { mutableStateOf(editingTask?.description ?: "") }
    var selectedPriority by remember { mutableStateOf(editingTask?.priority ?: TaskPriority.MEDIUM) }
    var requirePhotoAttachment by remember { mutableStateOf(editingTask?.requirePhotoProof ?: false) }
    var useFocusLock by remember { mutableStateOf(editingTask?.isHostageModeEnabled ?: false) }
    var currentPhotoPath by remember { mutableStateOf(editingTask?.photoPath) }
    var showTitleError by remember { mutableStateOf(false) }

    val defaultDeadline = remember {
        editingTask?.deadlineTimestamp ?: run {
            Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    var selectedDeadlineMs by remember { mutableLongStateOf(defaultDeadline) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val isCompletedTask = editingTask?.status == TaskStatus.COMPLETED
    val interactionMode by OnboardingPreferences.interactionModeFlow.collectAsState()
    val isStrictTaskLocked = isEditing && !InteractionModePolicy.canEditTask(interactionMode, isTaskSaved = true)
    val isDeadlineLocked = isCompletedTask || isStrictTaskLocked

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Row: Title on Left, Save pill on Right (matching media_1791911237037_9ee515f4.png)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit task" else "New task",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = {
                        if (title.isBlank()) {
                            showTitleError = true
                        } else {
                            onSave(
                                title.trim(),
                                description.trim(),
                                selectedDeadlineMs,
                                selectedPriority,
                                requirePhotoAttachment,
                                useFocusLock,
                                currentPhotoPath
                            )
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Task title
            Text(
                text = "Task title",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) showTitleError = false
                },
                placeholder = { Text("Review today's top priorities") },
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text("Enter a task title") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth().clarityMask()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: Due date and time
            Text(
                text = "Due date and time",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isStrictTaskLocked) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Deadlines are locked in Strict mode to preserve committed focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isCompletedTask) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Due time is locked for completed tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Pill
                Surface(
                    onClick = { if (!isDeadlineLocked) showDatePicker = true },
                    enabled = !isDeadlineLocked,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = if (isDeadlineLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dateFormatter.format(Date(selectedDeadlineMs)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isDeadlineLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Time Pill
                Surface(
                    onClick = { if (!isDeadlineLocked) showTimePicker = true },
                    enabled = !isDeadlineLocked,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = if (isDeadlineLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeFormatter.format(Date(selectedDeadlineMs)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isDeadlineLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section: Priority
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.entries.forEach { priority ->
                    val isSelected = selectedPriority == priority
                    Surface(
                        onClick = { if (!isStrictTaskLocked) selectedPriority = priority },
                        enabled = !isStrictTaskLocked,
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) {
                            when (priority) {
                                TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
                                TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primaryContainer
                                TaskPriority.LOW -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        } else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) {
                                when (priority) {
                                    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                                    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                                    TaskPriority.LOW -> MaterialTheme.colorScheme.outline
                                }
                            } else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = priority.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) {
                                    when (priority) {
                                        TaskPriority.HIGH -> MaterialTheme.colorScheme.onErrorContainer
                                        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.onPrimaryContainer
                                        TaskPriority.LOW -> MaterialTheme.colorScheme.onSurface
                                    }
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 3: Notes
            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Add any details or sub-steps...") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth().clarityMask()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Section 4: Prioritize in Focus Lock Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Prioritize in Focus Lock",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pin on pause screen if this task becomes overdue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = useFocusLock,
                        onCheckedChange = { useFocusLock = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDeadlineMs
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            shape = RoundedCornerShape(28.dp),
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMillis
                            }
                            val currentCal = Calendar.getInstance().apply {
                                timeInMillis = selectedDeadlineMs
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            }
                            selectedDeadlineMs = currentCal.timeInMillis
                        }
                        showDatePicker = false
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentCal = Calendar.getInstance().apply { timeInMillis = selectedDeadlineMs }
        val timePickerState = rememberTimePickerState(
            initialHour = currentCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = currentCal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Select due time",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedCal = Calendar.getInstance().apply {
                            timeInMillis = selectedDeadlineMs
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedDeadlineMs = updatedCal.timeInMillis
                        showTimePicker = false
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
