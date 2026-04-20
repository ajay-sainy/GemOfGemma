package com.gemofgemma.ui.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemofgemma.core.model.ChatMessage
import com.gemofgemma.core.model.ToolCategory
import com.gemofgemma.core.model.ToolDefinition
import com.gemofgemma.ui.camera.BoundingBoxOverlay
import com.gemofgemma.ui.components.AnimatedGemIcon
import com.gemofgemma.ui.components.FeatureChip
import com.gemofgemma.ui.components.ThinkingIndicator
import com.gemofgemma.ui.theme.GradientEnd
import com.gemofgemma.ui.theme.GradientStart
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    capturedImageBytes: ByteArray? = null,
    onCapturedImageConsumed: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCapture: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImagePicked(uri, context.contentResolver)
        }
    }

    // Mic permission
    var pendingMicAction by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        }
        pendingMicAction = false
    }
    val onMicToggle: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.toggleRecording()
        } else if (activity != null &&
            !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)
            && pendingMicAction
        ) {
            // Permission permanently denied — send user to app settings
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        } else {
            pendingMicAction = true
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Handle captured image from navigation
    LaunchedEffect(capturedImageBytes) {
        capturedImageBytes?.let {
            viewModel.attachImage(it)
            onCapturedImageConsumed()
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.streamingText, uiState.thinkingText) {
        if (uiState.messages.isNotEmpty() || uiState.streamingText != null) {
            // Scroll to the very bottom — account for streaming/thinking items after messages
            val extraItems = 1 + // top spacer
                uiState.messages.size +
                (if (uiState.thinkingText != null || uiState.streamingText != null) 1 else 0) +
                (if (uiState.isLoading && uiState.streamingText == null && uiState.thinkingText == null) 1 else 0) +
                1 // bottom spacer
            listState.animateScrollToItem(extraItems - 1)
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // ── Minimal header ───────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = "Gem of Gemma",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gem of Gemma",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = viewModel::toggleConversationHistory,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = "Chat History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = viewModel::newChat,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // ── Model offline banner ─────────────────────────────────
        AnimatedVisibility(
            visible = !uiState.isModelAvailable,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),     
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,      
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AI is offline",
                            style = MaterialTheme.typography.labelLarge,        
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer  
                        )
                        Text(
                            "Download the model in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text("Settings")
                    }
                }
            }
        }

        // ── Messages or empty state ──────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedGemIcon(size = 72.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Ask me anything",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Powered by Gemma 4 — running entirely on your device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant      
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),     
                        verticalArrangement = Arrangement.spacedBy(8.dp)        
                    ) {
                        val suggestions = listOf(
                            "Set an alarm for 7:00 AM",
                            "Turn on the flashlight",
                            "Write a short poem about the moon",
                            "Explain quantum computing simply",
                            "What can you help me with?"
                        )
                        suggestions.forEach { suggestion ->
                            FeatureChip(
                                label = "\"$suggestion\"",
                                onClick = {
                                    viewModel.onInputChanged(suggestion)
                                    keyboardController?.hide()
                                    viewModel.sendMessage()
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(uiState.messages, key = { it.id }) { message ->       
                        Column {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium      
                                    )
                                ) + fadeIn()
                            ) {
                                ChatBubble(message = message)
                            }
                        }
                    }

                    // Streaming response with thinking — single bubble
                    if (uiState.thinkingText != null || uiState.streamingText != null) {
                        item(key = "streaming") {
                            val hasResponse = !uiState.streamingText.isNullOrEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(0.85f, fill = false)
                                        .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        ),
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = 6.dp,
                                        bottomEnd = 20.dp
                                    ),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 1.dp
                                ) {
                                    Column {
                                        uiState.thinkingText?.let { thinking ->
                                            ThinkingSection(
                                                thinkingText = thinking,
                                                isStreaming = true,
                                                autoCollapse = hasResponse
                                            )
                                        }
                                        if (hasResponse) {
                                            Text(
                                                text = uiState.streamingText!!,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Thinking indicator (dots) when waiting for first token
                    if (uiState.isLoading && uiState.streamingText == null && uiState.thinkingText == null) {   
                        item(key = "thinking") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically, 
                                modifier = Modifier.padding(vertical = 4.dp)    
                            ) {
                                AnimatedGemIcon(size = 24.dp)
                                ThinkingIndicator()
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }

        // ── Input area ───────────────────────────────────────────
        Column(modifier = Modifier.navigationBarsPadding()) {
            // Pending image preview
            AnimatedVisibility(
                visible = uiState.pendingImageThumbnail != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                uiState.pendingImageThumbnail?.let { thumbBytes ->
                    PendingImagePreview(
                        thumbnailBytes = thumbBytes,
                        onRemove = viewModel::removeAttachment
                    )
                }
            }

            // Input bar with attachment popup overlay
            Box {
                ChatInputBar(
                    input = uiState.currentInput,
                    onInputChanged = viewModel::onInputChanged,
                    onSend = {
                        keyboardController?.hide()
                        viewModel.sendMessage()
                    },
                    onMicToggle = onMicToggle,
                    onStop = viewModel::stopGeneration,
                    onAttachmentClick = viewModel::toggleAttachmentOptions,
                    isRecording = uiState.isRecording,
                    isLoading = uiState.isLoading,
                    isEngineReady = uiState.isEngineReady,
                    hasAttachment = uiState.pendingImageBytes != null
                )

                // Attachment menu (Camera / Gallery / Tools)
                DropdownMenu(
                    expanded = uiState.showAttachmentOptions,
                    onDismissRequest = { viewModel.toggleAttachmentOptions() }
                ) {
                    DropdownMenuItem(
                        text = { Text("Camera", fontWeight = FontWeight.Medium) },
                        onClick = {
                            viewModel.toggleAttachmentOptions()
                            onNavigateToCapture()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gallery", fontWeight = FontWeight.Medium) },
                        onClick = {
                            viewModel.toggleAttachmentOptions()
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Tools" + if (uiState.enabledTools.isNotEmpty()) " (${uiState.enabledTools.size} active)" else "",
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            viewModel.toggleAttachmentOptions()
                            viewModel.toggleToolPicker()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }

    // Tool picker bottom sheet
    if (uiState.showToolPicker) {
        ToolPickerBottomSheet(
            enabledTools = uiState.enabledTools,
            onToggleTool = viewModel::toggleTool,
            onEnableAll = { viewModel.setAllToolsEnabled(true) },
            onDisableAll = { viewModel.setAllToolsEnabled(false) },
            onDismiss = viewModel::hideToolPicker
        )
    }

    // Conversation history bottom sheet
    if (uiState.showConversationHistory) {
        ConversationHistorySheet(
            conversations = uiState.conversations,
            currentConversationId = uiState.currentConversationId,
            onSelect = viewModel::loadConversation,
            onDelete = viewModel::deleteConversation,
            onDismiss = viewModel::hideConversationHistory
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val hasImage = message.imageBytes != null
    val context = LocalContext.current
    var showCopyButton by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
        Surface(
            modifier = Modifier
                .weight(0.85f, fill = false)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .then(
                    if (!isUser) Modifier.clickable { showCopyButton = !showCopyButton }
                    else Modifier
                ),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            color = when {
                message.messageType == ChatMessage.MessageType.ERROR ->
                    MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            tonalElevation = if (isUser) 0.dp else 1.dp
        ) {
            Column {
                // Show collapsible thinking section for finalized messages
                val thinking = message.thinkingContent
                if (!isUser && thinking != null) {
                    ThinkingSection(
                        thinkingText = thinking,
                        isStreaming = false
                    )
                }

                when (message.messageType) {
                    ChatMessage.MessageType.IMAGE_QUERY -> ImageQueryContent(message, isUser)
                    ChatMessage.MessageType.DETECTION -> DetectionContent(message)
                    ChatMessage.MessageType.OCR -> OcrContent(message)
                    ChatMessage.MessageType.ACTION -> ActionContent(message)
                    ChatMessage.MessageType.ERROR -> ErrorContent(message)
                    else -> TextContent(message, isUser)
                }
            }
        }
        }

        // Copy button for assistant messages — shown on tap
        AnimatedVisibility(
            visible = !isUser && message.content.isNotBlank() && showCopyButton
        ) {
            var copied by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GemOfGemma", message.content))
                        copied = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy response",
                        modifier = Modifier.size(14.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (copied) {
                    Text(
                        text = "Copied",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        copied = false
                    }
                }
            }
        }
    }
}

@Composable
private fun TextContent(message: ChatMessage, isUser: Boolean) {
    Text(
        text = message.content,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isUser)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ImageQueryContent(message: ChatMessage, isUser: Boolean) {
    val bitmap = remember(message.id) {
        message.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Attached image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                ),
            contentScale = ContentScale.Crop
        )
    }
    if (message.content.isNotBlank()) {
        Text(
            text = message.content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetectionContent(message: ChatMessage) {
    val bitmap = remember(message.id) {
        message.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    val detections = message.detections.orEmpty()
    if (bitmap != null && detections.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Detected objects",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            BoundingBoxOverlay(
                detections = detections,
                imageWidth = 1000,
                imageHeight = 1000,
                modifier = Modifier.matchParentSize()
            )
        }
    }

    // Detection summary
    val summary = if (detections.isNotEmpty()) {
        val grouped = detections.groupBy { it.label.lowercase() }
        val parts = grouped.map { (label, items) ->
            if (items.size > 1) "${items.size} ${label}s" else label
        }
        "Found ${detections.size} object${if (detections.size != 1) "s" else ""}: ${parts.joinToString(", ")}"
    } else {
        message.content
    }

    Text(
        text = summary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun OcrContent(message: ChatMessage) {
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = "Extracted Text",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            onClick = {
                clipboardManager.setText(AnnotatedString(message.content))
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.align(Alignment.End)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ActionContent(message: ChatMessage) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorContent(message: ChatMessage) {
    Text(
        text = message.content,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer
    )
}

@Composable
private fun ThinkingSection(
    thinkingText: String,
    isStreaming: Boolean,
    autoCollapse: Boolean = false,
    modifier: Modifier = Modifier
) {
    // During streaming: expanded until response starts, then auto-collapse.
    // After streaming: default collapsed.
    var expanded by remember { mutableStateOf(isStreaming) }

    // Auto-collapse when response text starts appearing
    LaunchedEffect(autoCollapse) {
        if (autoCollapse) expanded = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83D\uDCAD",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = if (isStreaming && !autoCollapse) "Thinking\u2026" else "Thought process",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isStreaming && !autoCollapse) {
                    ThinkingIndicator(
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "\u25BE" else "\u25B8",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Collapsible content
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thinkingText,
                    modifier = Modifier.padding(
                        start = 10.dp,
                        end = 10.dp,
                        bottom = 8.dp
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    maxLines = if (isStreaming) Int.MAX_VALUE else 50,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
    onStop: () -> Unit,
    onAttachmentClick: () -> Unit,
    isRecording: Boolean,
    isLoading: Boolean,
    isEngineReady: Boolean = true,
    hasAttachment: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasText = input.isNotBlank()
    val canSend = (hasText || hasAttachment) && !isLoading && isEngineReady

    // Three-state button: STOP (2) when loading, SEND (1) when text, MIC (0) otherwise
    val buttonState = when {
        isLoading -> 2
        hasText -> 1
        else -> 0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Engine loading banner
        AnimatedVisibility(visible = !isEngineReady) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer  
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI model is loading…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer  
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Single "+" attach button
                IconButton(
                    onClick = onAttachmentClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (hasAttachment)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = if (hasAttachment)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Pill-shaped text field with mic/send inside
                TextField(
                    value = input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (isRecording) "Listening…"
                                else if (!isEngineReady) "AI loading…"
                                else "Message…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        Crossfade(
                            targetState = buttonState,
                            animationSpec = tween(200),
                            label = "input_action"
                        ) { state ->
                            when (state) {
                                2 -> {
                                    IconButton(
                                        onClick = onStop,
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = "Stop generating",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                1 -> {
                                    IconButton(
                                        onClick = onSend,
                                        enabled = canSend,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (canSend)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                else -> {
                                    IconButton(
                                        onClick = onMicToggle,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = if (isRecording) "Stop recording" else "Voice input",
                                            tint = if (isRecording)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ── Pending Image Preview ────────────────────────────────────────────
@Composable
private fun PendingImagePreview(
    thumbnailBytes: ByteArray,
    onRemove: () -> Unit
) {
    val bitmap = remember(thumbnailBytes) {
        BitmapFactory.decodeByteArray(thumbnailBytes, 0, thumbnailBytes.size)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(modifier = Modifier.size(60.dp)) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Pending image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                // Remove button
                Surface(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.padding(3.dp),
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}

// ── Tool Picker Bottom Sheet ─────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun ToolPickerBottomSheet(
    enabledTools: Set<String>,
    onToggleTool: (String, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEnableAll) {
                    Text("Enable All")
                }
                TextButton(onClick = onDisableAll) {
                    Text("Disable All")
                }
            }

            Text(
                text = "${enabledTools.size} of ${ToolDefinition.ALL_TOOLS.size} active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Search / filter field
            var filterQuery by remember { mutableStateOf("") }
            TextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        "Filter tools\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Group and filter tools by category
            val query = filterQuery.trim()
            val grouped = ToolDefinition.ALL_TOOLS
                .filter { tool ->
                    query.isEmpty() ||
                        tool.name.contains(query, ignoreCase = true) ||
                        tool.description.contains(query, ignoreCase = true)
                }
                .groupBy { it.category }
            LazyColumn(
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                ToolCategory.entries.forEach { category ->
                    val toolsInCategory = grouped[category] ?: return@forEach
                    item(key = "header_${category.name}") {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(
                        count = toolsInCategory.size,
                        key = { toolsInCategory[it].id }
                    ) { index ->
                        val tool = toolsInCategory[index]
                        val isEnabled = enabledTools.contains(tool.id)
                        ToolPickerItem(
                            tool = tool,
                            isEnabled = isEnabled,
                            onToggle = { checked -> onToggleTool(tool.id, checked) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ToolPickerItem(
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
                onToggle(false)
            }
        }

        ListItem(
            headlineContent = { Text(tool.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(tool.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
            supportingContent = { Text(tool.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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

// ── Conversation History Bottom Sheet ────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationHistorySheet(
    conversations: List<com.gemofgemma.core.data.ConversationEntity>,
    currentConversationId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Conversations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (conversations.isEmpty()) {
                Text(
                    text = "No conversations yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(
                        count = conversations.size,
                        key = { conversations[it].id }
                    ) { index ->
                        val conv = conversations[index]
                        val isCurrent = conv.id == currentConversationId
                        val dateText = remember(conv.updatedAt) {
                            val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(conv.updatedAt))
                        }

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = conv.title,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                if (!isCurrent) {
                                    IconButton(
                                        onClick = { onDelete(conv.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onSelect(conv.id) },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isCurrent)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
