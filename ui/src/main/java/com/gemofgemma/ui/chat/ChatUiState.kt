package com.gemofgemma.ui.chat

import com.gemofgemma.core.data.ConversationEntity
import com.gemofgemma.core.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val streamingText: String? = null,
    val thinkingText: String? = null,
    val error: String? = null,
    val isModelAvailable: Boolean = false,
    val isEngineReady: Boolean = false,
    val pendingImageBytes: ByteArray? = null,
    val pendingImageThumbnail: ByteArray? = null,
    val showCameraCapture: Boolean = false,
    val showImagePicker: Boolean = false,
    val showAttachmentOptions: Boolean = false,
    val showToolPicker: Boolean = false,
    val enabledTools: Set<String> = emptySet(),
    val currentConversationId: String? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val showConversationHistory: Boolean = false
)
