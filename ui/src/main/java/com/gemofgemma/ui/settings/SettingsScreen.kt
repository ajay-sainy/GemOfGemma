package com.gemofgemma.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemofgemma.core.model.ToolCategory
import com.gemofgemma.core.model.ToolDefinition
import com.gemofgemma.ui.model.ModelStatusViewModel
import com.gemofgemma.ui.model.formatSize
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun SettingsScreen(
    viewModel: ModelStatusViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val enabledTools by settingsViewModel.enabledTools.collectAsStateWithLifecycle()
    
    val isModelAvailable by viewModel.isModelAvailable.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalBytes.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()

    val toolsByCategory = remember {
        ToolDefinition.ALL_TOOLS.groupBy { it.category }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // ── Model ────────────────────────────────────────────────
        item { SectionHeader(title = "Model", modifier = Modifier.padding(bottom = 8.dp)) }
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Gemma 4 E2B", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Column {
                                when {
                                    isModelAvailable -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Downloaded \u2713",
                                                color = MaterialTheme.colorScheme.tertiary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    isDownloading -> {
                                        Text(
                                            "Downloading (${(downloadProgress * 100).toInt()}%)\u2026",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    downloadError != null -> {
                                        Text(
                                            "Download failed",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            downloadError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                    else -> {
                                        Text("Not downloaded")
                                    }
                                }
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            when {
                                isModelAvailable -> {
                                    TextButton(
                                        onClick = { viewModel.deleteModel() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete")
                                    }
                                }
                                isDownloading -> {}
                                else -> {
                                    val hasPartial = remember { viewModel.hasPartialDownload() }
                                    TextButton(onClick = { viewModel.clearError(); viewModel.downloadModel() }) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (hasPartial) "Resume" else "Download")
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Storage Used") },
                        supportingContent = {
                            val sizeOnDisk = remember(isModelAvailable) { viewModel.getModelSizeOnDisk() }
                            Text(if (isModelAvailable) "${formatSize(sizeOnDisk)} used" else "0 MB / 2.58 GB")
                        },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        // ── Autonomous Tools (Dynamic Opt-In) ──────────────────────────────────────────
        
        toolsByCategory.forEach { (category, tools) ->
            item { SectionHeader(title = category.title, modifier = Modifier.padding(bottom = 8.dp)) }
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column {
                        tools.forEachIndexed { index, tool ->
                            ToolListItem(
                                tool = tool,
                                isEnabled = enabledTools.contains(tool.id),
                                onToggle = { enabled ->
                                    settingsViewModel.toggleTool(tool, enabled)
                                }
                            )
                            if (index < tools.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Appearance ───────────────────────────────────────────
        item { SectionHeader(title = "Appearance", modifier = Modifier.padding(bottom = 8.dp)) }
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                var darkMode by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    supportingContent = { Text("Follow system default") },
                    leadingContent = {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { darkMode = it }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ToolListItem(
    tool: ToolDefinition,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val perm = tool.requiredPermission
    if (perm != null) {
        val permissionState = rememberPermissionState(perm)
        var triggerToggled by remember { mutableStateOf(false) }

        LaunchedEffect(permissionState.status.isGranted, triggerToggled) {
            if (triggerToggled && permissionState.status.isGranted && !isEnabled) {
                onToggle(true)
                triggerToggled = false
            } else if (!permissionState.status.isGranted && isEnabled) {
                // Automatically disable if permission was revoked from settings
                onToggle(false)
            }
        }

        ListItem(
            headlineContent = { Text(tool.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(tool.description) },
            leadingContent = {
                val icon = when {
                    tool.isDangerousPermission -> Icons.Default.Warning
                    else -> Icons.Default.Settings
                }
                val tint = if (tool.isDangerousPermission) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(icon, contentDescription = null, tint = tint)
            },
            trailingContent = {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (permissionState.status.isGranted) {
                                onToggle(true)
                            } else {
                                triggerToggled = true
                                permissionState.launchPermissionRequest()
                            }
                        } else {
                            onToggle(false)
                        }
                    }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    } else {
        ListItem(
            headlineContent = { Text(tool.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(tool.description) },
            leadingContent = {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked -> onToggle(checked) }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 8.dp)
    )
}
