package com.example.dueordie.ui.notion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionSyncScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NotionSyncViewModel = viewModel(factory = NotionSyncViewModel.Factory(LocalContext.current))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tokenVisible by remember { mutableStateOf(false) }
    var guideExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            if ((text.startsWith("secret_") || text.startsWith("ntn_")) && state.token.isBlank()) {
                viewModel.setToken(text)
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PullToRefreshBox(
        isRefreshing = state.isSyncing,
        onRefresh = {
            if (state.isConnected) {
                viewModel.syncNow()
            } else if (state.token.isNotBlank()) {
                viewModel.connect()
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = statusBarTop + 16.dp,
                    bottom = navBarBottom + 32.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_revert),
                        contentDescription = "Back to Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Notion", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Keep your tasks backed up in your workspace.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.status, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = if (state.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(
                        text = if (state.isConnected) {
                            (state.databaseTitle ?: "Notion database connected").replace("—", " - ").replace("–", " - ")
                        } else "Connect a shared page or database to start automatic sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.error != null) Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!state.isConnected) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connect your workspace", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.notion.so/profile/integrations")))
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1-Tap: Open Notion Integrations in Browser")
                        }

                        OutlinedTextField(
                            value = state.token,
                            onValueChange = viewModel::setToken,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Integration token") },
                            placeholder = { Text("ntn_… or secret_…") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PasteButton(context, viewModel::setToken)
                                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                        Icon(if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (tokenVisible) "Hide token" else "Show token")
                                    }
                                }
                            }
                        )
                        OutlinedTextField(
                            value = state.target,
                            onValueChange = viewModel::setTarget,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Page or database link (optional)") },
                            placeholder = { Text("Leave blank to find a shared page") },
                            singleLine = true,
                            trailingIcon = { PasteButton(context, viewModel::setTarget) }
                        )
                        Button(
                            onClick = viewModel::connect,
                            enabled = !state.isSyncing,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                            }
                            Text(if (state.isSyncing) "Connecting and syncing..." else "Connect & Auto-Setup Notion")
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("How to connect (about 2 mins)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { guideExpanded = !guideExpanded }) {
                                Icon(if (guideExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (guideExpanded) "Hide setup guide" else "Show setup guide")
                            }
                        }
                        AnimatedVisibility(guideExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.notion.so/my-integrations"))) }
                                }) { Text("Open Notion integrations") }
                                Text("1. Create an internal integration and copy its token.", style = MaterialTheme.typography.bodySmall)
                                Text("2. In Notion, share a page with that integration using Options (...) -> Connections. Leave the link blank to let Mora find an accessible page, or paste a specific page/database link.", style = MaterialTheme.typography.bodySmall)
                                Text("3. Paste the token and connect. Mora creates “Mora - Today's Plan” with Task, Priority, Today's Plan, When, and Notes columns.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-sync tasks to Notion", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Automatically create and update tasks when they change here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = state.autoSync, onCheckedChange = viewModel::setAutoSync)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("Synced", state.syncedCount.toString())
                            Metric("Pending", state.pendingCount.toString())
                            Metric("Last synced", state.lastSyncTimestamp?.let(::formatTimestamp) ?: "Never")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = viewModel::syncNow,
                                enabled = !state.isSyncing,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isSyncing) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                                else Text("Sync All Now")
                            }
                            OutlinedButton(
                                onClick = viewModel::disconnect,
                                enabled = !state.isSyncing,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Disconnect") }
                        }
                        Text("Disconnecting removes the saved token from this device. Your local tasks stay here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteButton(context: Context, onPaste: (String) -> Unit) {
    TextButton(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (value.isNotBlank()) onPaste(value)
    }) { Text("Paste") }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTimestamp(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
