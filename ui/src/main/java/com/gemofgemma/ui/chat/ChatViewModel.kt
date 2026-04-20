package com.gemofgemma.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemofgemma.core.AiProcessor
import com.gemofgemma.core.data.ChatRepository
import com.gemofgemma.core.data.ToolPreferencesRepository
import com.gemofgemma.core.model.AiRequest
import com.gemofgemma.core.model.AiResponse
import com.gemofgemma.core.model.ChatMessage
import com.gemofgemma.voice.VoiceRecognizer
import com.gemofgemma.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiProcessor: AiProcessor,
    private val voiceRecognizer: VoiceRecognizer,
    private val toolPreferencesRepository: ToolPreferencesRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentConversationId: String = UUID.randomUUID().toString()
    private var voiceJob: Job? = null
    private var generationJob: Job? = null

    companion object {
        private const val THUMBNAIL_MAX_DIMENSION = 256
    }

    init {
        viewModelScope.launch {
            toolPreferencesRepository.initializeDefaultsIfNeeded()
        }
        viewModelScope.launch {
            aiProcessor.isModelAvailable.collect { available ->
                _uiState.update { it.copy(isModelAvailable = available) }
            }
        }
        viewModelScope.launch {
            aiProcessor.isEngineReady.collect { ready ->
                _uiState.update { it.copy(isEngineReady = ready) }
            }
        }
        viewModelScope.launch {
            toolPreferencesRepository.enabledToolsFlow.collect { tools ->
                _uiState.update { it.copy(enabledTools = tools) }
            }
        }
        viewModelScope.launch {
            chatRepository.getConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
        viewModelScope.launch {
            initializeConversation()
        }
    }

    private suspend fun initializeConversation() {
        val latest = chatRepository.getLatestConversation()
        if (latest != null) {
            loadConversation(latest.id)
        } else {
            val newConv = chatRepository.createConversation()
            currentConversationId = newConv.id
            _uiState.update { it.copy(currentConversationId = newConv.id) }
        }
    }

    fun onInputChanged(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    // --- Attachment management ---

    fun attachImage(bytes: ByteArray) {
        viewModelScope.launch {
            val thumbnail = withContext(Dispatchers.Default) { generateThumbnail(bytes) }
            _uiState.update {
                it.copy(
                    pendingImageBytes = bytes,
                    pendingImageThumbnail = thumbnail,
                    showAttachmentOptions = false
                )
            }
        }
    }

    fun removeAttachment() {
        _uiState.update {
            it.copy(
                pendingImageBytes = null,
                pendingImageThumbnail = null
            )
        }
    }

    fun toggleAttachmentOptions() {
        _uiState.update { it.copy(showAttachmentOptions = !it.showAttachmentOptions) }
    }

    fun showCameraCapture() {
        _uiState.update { it.copy(showCameraCapture = true, showAttachmentOptions = false) }
    }

    fun hideCameraCapture() {
        _uiState.update { it.copy(showCameraCapture = false) }
    }

    fun onImagePicked(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext null
                    // Decode, resize, and re-encode as JPEG for model compatibility
                    val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        ?: return@withContext rawBytes // Can't decode — pass raw and let model try
                    val maxDim = 1024
                    val scale = maxDim.toFloat() / maxOf(original.width, original.height)
                    if (scale < 1f) {
                        val w = (original.width * scale).toInt().coerceAtLeast(1)
                        val h = (original.height * scale).toInt().coerceAtLeast(1)
                        val resized = Bitmap.createScaledBitmap(original, w, h, true)
                        val out = java.io.ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        if (resized !== original) resized.recycle()
                        original.recycle()
                        out.toByteArray()
                    } else {
                        // Already small — re-encode as JPEG to ensure format compatibility
                        val out = java.io.ByteArrayOutputStream()
                        original.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        original.recycle()
                        out.toByteArray()
                    }
                }
                if (bytes != null) {
                    attachImage(bytes)
                } else {
                    _uiState.update { it.copy(error = "Could not read image") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load image: ${e.message}") }
            }
        }
    }

    private fun generateThumbnail(bytes: ByteArray): ByteArray {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val scale = THUMBNAIL_MAX_DIMENSION.toFloat() / maxOf(original.width, original.height)
        if (scale >= 1f) return bytes
        val thumbWidth = (original.width * scale).toInt().coerceAtLeast(1)
        val thumbHeight = (original.height * scale).toInt().coerceAtLeast(1)
        val thumbnail = Bitmap.createScaledBitmap(original, thumbWidth, thumbHeight, true)
        val out = ByteArrayOutputStream()
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 75, out)
        if (thumbnail !== original) thumbnail.recycle()
        original.recycle()
        return out.toByteArray()
    }

    // --- Messaging ---

    fun sendMessage() {
        val message = _uiState.value.currentInput.trim()
        if (message.isEmpty()) return

        val pendingImage = _uiState.value.pendingImageBytes
        val hasImage = pendingImage != null

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatMessage.Role.USER,
            content = message,
            imageBytes = pendingImage,
            messageType = if (hasImage) ChatMessage.MessageType.IMAGE_QUERY else ChatMessage.MessageType.TEXT
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                currentInput = "",
                isLoading = true,
                error = null,
                streamingText = null,
                thinkingText = null,
                pendingImageBytes = null,
                pendingImageThumbnail = null,
                showAttachmentOptions = false
            )
        }

        // Persist user message and auto-set title from first user message
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.saveMessage(currentConversationId, userMessage)
            if (_uiState.value.messages.count { it.role == ChatMessage.Role.USER } == 1) {
                val title = message.take(50)
                chatRepository.updateConversationTitle(currentConversationId, title)
            }
        }

        generationJob = viewModelScope.launch {
            val conversationId = currentConversationId
            val request = if (pendingImage != null) {
                AiRequest.VisionChat(
                    message = message,
                    imageBytes = pendingImage,
                    conversationId = conversationId,
                    history = _uiState.value.messages
                )
            } else {
                AiRequest.TextChat(
                    message = message,
                    conversationId = conversationId,
                    history = _uiState.value.messages
                )
            }

            try {
                // Try streaming first
                var finalText = ""
                var finalThinking: String? = null
                var finalActionResult: com.gemofgemma.core.model.ActionResult? = null
                var streamingFailed = false
                aiProcessor.processStreaming(request)
                    .onEach { chunk ->
                        finalText = chunk.responseText
                        finalThinking = chunk.thinkingText
                        if (chunk.actionResult != null) {
                            finalActionResult = chunk.actionResult
                        }
                        _uiState.update { it.copy(
                            streamingText = chunk.responseText,
                            thinkingText = chunk.thinkingText
                        ) }
                    }
                    .catch { e ->
                        // Streaming failed mid-way — mark for fallback
                        android.util.Log.e("ChatVM", "Streaming catch: ${e.message}", e)
                        streamingFailed = true
                        _uiState.update { it.copy(streamingText = null, thinkingText = null) }
                        emit(com.gemofgemma.core.model.StreamChunk("")) // terminate the flow
                    }
                    .collect { /* consumed by onEach above */ }

                if (streamingFailed) {
                    // Fall back to blocking path
                    val response = aiProcessor.process(request)
                    val assistantMessage = buildAssistantMessage(response, pendingImage)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isLoading = false,
                            streamingText = null,
                            thinkingText = null
                        )
                    }
                    persistAssistantMessage(assistantMessage)
                    return@launch
                }

                // Stream completed — post-process the final text
                _uiState.update { it.copy(streamingText = null, thinkingText = null) }

                val response = if (finalActionResult != null) {
                    // Tool call was handled during streaming via native ToolSet
                    com.gemofgemma.core.model.AiResponse.ActionResponse(finalActionResult!!)
                } else when (request) {
                    is AiRequest.VisionChat ->
                        aiProcessor.postProcessVisionChat(finalText, message)
                    is AiRequest.TextChat ->
                        aiProcessor.postProcessChat(finalText)
                }

                val assistantMessage = buildAssistantMessage(response, pendingImage, finalThinking)
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isLoading = false,
                        streamingText = null,
                        thinkingText = null
                    )
                }
                persistAssistantMessage(assistantMessage)
            } catch (e: Exception) {
                // processStreaming threw (e.g. engine not ready) — fall back
                _uiState.update { it.copy(streamingText = null, thinkingText = null) }
                try {
                    val response = aiProcessor.process(request)
                    val assistantMessage = buildAssistantMessage(response, pendingImage)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isLoading = false,
                            streamingText = null,
                            thinkingText = null
                        )
                    }
                    persistAssistantMessage(assistantMessage)
                } catch (fallbackError: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            streamingText = null,
                            thinkingText = null,
                            error = "Failed to get response: ${fallbackError.message}"
                        )
                    }
                }
            }
        }
    }

    private fun persistAssistantMessage(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.saveMessage(currentConversationId, message)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null

        val currentStreamingText = _uiState.value.streamingText
        val currentThinking = _uiState.value.thinkingText

        if (!currentStreamingText.isNullOrBlank()) {
            val stoppedMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.ASSISTANT,
                content = currentStreamingText + " ⏹",
                thinkingContent = currentThinking
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + stoppedMessage,
                    isLoading = false,
                    streamingText = null,
                    thinkingText = null
                )
            }
            persistAssistantMessage(stoppedMessage)
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    streamingText = null,
                    thinkingText = null
                )
            }
        }

        viewModelScope.launch {
            aiProcessor.cancelGeneration()
        }
    }

    private fun buildAssistantMessage(
        response: AiResponse,
        pendingImage: ByteArray?,
        thinkingContent: String? = null
    ): ChatMessage {
        return when (response) {
            is AiResponse.TextResponse -> ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.ASSISTANT,
                content = response.text,
                thinkingContent = thinkingContent
            )
            is AiResponse.ActionResponse -> {
                val resultText = when (val result = response.actionResult) {
                    is com.gemofgemma.core.model.ActionResult.Success -> result.message
                    is com.gemofgemma.core.model.ActionResult.Error -> "Action failed: ${result.message}"
                    is com.gemofgemma.core.model.ActionResult.PermissionRequired ->
                        "Permissions needed: ${result.permissions.joinToString()}"
                    is com.gemofgemma.core.model.ActionResult.NeedsConfirmation ->
                        "Confirm action: ${result.actionDescription}"
                }
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatMessage.Role.ASSISTANT,
                    content = resultText,
                    messageType = ChatMessage.MessageType.ACTION
                )
            }
            is AiResponse.DetectionResponse -> ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.ASSISTANT,
                content = "Detected ${response.detections.size} object(s): " +
                    response.detections.joinToString { it.label },
                imageBytes = pendingImage,
                detections = response.detections,
                messageType = ChatMessage.MessageType.DETECTION
            )
            is AiResponse.OcrResponse -> ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.ASSISTANT,
                content = response.blocks.joinToString("\n") { it.text },
                imageBytes = pendingImage,
                ocrBlocks = response.blocks,
                messageType = ChatMessage.MessageType.OCR
            )
            is AiResponse.ErrorResponse -> ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.ASSISTANT,
                content = "Error: ${response.message}",
                messageType = ChatMessage.MessageType.ERROR
            )
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _uiState.update { it.copy(isRecording = true, error = null) }

        voiceJob = viewModelScope.launch {
            voiceRecognizer.startListening().collect { state ->
                when (state) {
                    is VoiceState.PartialResult -> {
                        _uiState.update { it.copy(currentInput = state.text) }
                    }
                    is VoiceState.Result -> {
                        _uiState.update {
                            it.copy(
                                currentInput = state.text,
                                isRecording = false
                            )
                        }
                        if (state.text.isNotBlank()) {
                            sendMessage()
                        }
                    }
                    is VoiceState.Error -> {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                error = state.message
                            )
                        }
                    }
                    is VoiceState.Listening -> { /* already showing recording state */ }
                    is VoiceState.Idle -> {
                        _uiState.update { it.copy(isRecording = false) }
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        voiceRecognizer.stopListening()
        voiceJob?.cancel()
        voiceJob = null
        _uiState.update { it.copy(isRecording = false) }
    }

    // --- Tool picker ---

    fun toggleToolPicker() {
        _uiState.update { it.copy(showToolPicker = !it.showToolPicker) }
    }

    fun hideToolPicker() {
        _uiState.update { it.copy(showToolPicker = false) }
    }

    fun toggleTool(toolId: String, enabled: Boolean) {
        viewModelScope.launch {
            toolPreferencesRepository.setToolEnabled(toolId, enabled)
        }
    }

    fun setAllToolsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val allIds = com.gemofgemma.core.model.ToolDefinition.ALL_TOOLS.map { it.id }.toSet()
            toolPreferencesRepository.setAllToolsEnabled(allIds, enabled)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearChat() {
        viewModelScope.launch {
            aiProcessor.resetChat(currentConversationId)
            _uiState.update { it.copy(messages = emptyList(), error = null) }
        }
    }

    fun newChat() {
        viewModelScope.launch {
            // Cancel any in-progress generation
            generationJob?.cancel()
            generationJob = null

            val oldId = currentConversationId
            val newConv = chatRepository.createConversation()
            currentConversationId = newConv.id

            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    currentConversationId = newConv.id,
                    isLoading = false,
                    streamingText = null,
                    thinkingText = null,
                    error = null,
                    showConversationHistory = false
                )
            }

            aiProcessor.resetChat(oldId)
        }
    }

    fun loadConversation(id: String) {
        viewModelScope.launch {
            generationJob?.cancel()
            generationJob = null

            val oldId = currentConversationId
            currentConversationId = id

            val messages = chatRepository.loadMessages(id)
            _uiState.update {
                it.copy(
                    messages = messages,
                    currentConversationId = id,
                    isLoading = false,
                    streamingText = null,
                    thinkingText = null,
                    error = null,
                    showConversationHistory = false
                )
            }

            if (oldId != id) {
                aiProcessor.resetChat(oldId)
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (id == currentConversationId) {
                newChat()
            }
        }
    }

    fun toggleConversationHistory() {
        _uiState.update { it.copy(showConversationHistory = !it.showConversationHistory) }
    }

    fun hideConversationHistory() {
        _uiState.update { it.copy(showConversationHistory = false) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognizer.cancel()
    }
}
