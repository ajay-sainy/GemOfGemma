package com.gemofgemma.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gemofgemma.actions.ActionDispatcher
import com.gemofgemma.actions.model.ParsedAction
import com.gemofgemma.ai.model.ModelDownloadManager
import com.gemofgemma.ai.parsers.DetectionResponseParser
import com.gemofgemma.ai.prompts.PromptRouter
import com.gemofgemma.ai.tools.GemmaToolSet
import com.gemofgemma.core.model.AiRequest
import com.gemofgemma.core.model.AiResponse
import com.gemofgemma.core.model.ChatMessage
import com.gemofgemma.core.model.StreamChunk
import com.google.ai.edge.litertlm.Channel
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that loads Gemma 4 E2B via LiteRT-LM, maintains a warm
 * engine instance, and exposes [process] for all AI requests.
 *
 * Bind to this service from ViewModels via [GemmaBinder].
 */
@AndroidEntryPoint
class GemmaService : LifecycleService() {

    @Inject lateinit var engine: GemmaEngine
    @Inject lateinit var promptRouter: PromptRouter
    @Inject lateinit var detectionParser: DetectionResponseParser
    @Inject lateinit var actionDispatcher: ActionDispatcher

    /** Native LiteRT-LM ToolSet — created lazily after Hilt injection. */
    private val gemmaToolSet by lazy { GemmaToolSet(actionDispatcher) }

    @Inject lateinit var modelDownloadManager: ModelDownloadManager

    private val binder = GemmaBinder()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    inner class GemmaBinder : Binder() {
        fun getService(): GemmaService = this@GemmaService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing…"))

        // Track download progress in the foreground notification
        lifecycleScope.launch {
            modelDownloadManager.downloadProgress.collect { progress ->
                if (modelDownloadManager.isDownloading.value) {
                    val pct = (progress * 100).toInt()
                    val downloaded = modelDownloadManager.downloadedBytes.value
                    val total = modelDownloadManager.totalBytes.value
                    updateNotification(
                        "Downloading Gemma 4: $pct% (${formatSize(downloaded)} / ${formatSize(total)})",
                        pct
                    )
                }
            }
        }

        // Initialize engine if model is already available
        lifecycleScope.launch(Dispatchers.IO) {
            if (modelDownloadManager.isModelAvailable()) {
                initializeEngine()
            } else {
                Log.i(TAG, "Model not available — waiting for user-initiated download")
                updateNotification("Waiting for model download")
            }
        }

        // Watch for model becoming available after user-triggered download
        lifecycleScope.launch {
            modelDownloadManager.isModelAvailableFlow.collect { available ->
                if (available && !engine.isInitialized) {
                    withContext(Dispatchers.IO) { initializeEngine() }
                }
            }
        }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    /**
     * Process an AI request end-to-end. For vision/chat requests, returns text
     * or detection results. For voice commands, parses function calls and
     * dispatches actions via [ActionDispatcher].
     */
    suspend fun process(request: AiRequest): AiResponse {
        if (!engine.isInitialized) {
            val reason = engine.initError ?: "Model may still be loading."
            return AiResponse.ErrorResponse("Engine not initialized. $reason")
        }
        return withContext(Dispatchers.IO) {
            try {
                when (request) {
                    is AiRequest.TextChat -> processChat(request)
                    is AiRequest.VisionChat -> processVisionChat(request)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                AiResponse.ErrorResponse("Processing failed: ${e.message}", e)
            }
        }
    }

    /**
     * Stream tokens for a chat request. Each emission is the accumulated
     * response text so far. Only supports TextChat and VisionChat.
     * Post-processing (function calls, detection parsing) should be done
     * by the caller on the final emitted text.
     */
    suspend fun processStreaming(request: AiRequest): Flow<StreamChunk> {
        if (!engine.isInitialized) {
            throw IllegalStateException(
                "Engine not initialized. ${engine.initError ?: "Model may still be loading."}"
            )
        }
        return when (request) {
            is AiRequest.TextChat -> processChatStreaming(request)
            is AiRequest.VisionChat -> processVisionChatStreaming(request)
        }
    }

    private suspend fun processChatStreaming(request: AiRequest.TextChat): Flow<StreamChunk> {
        val systemPrompt = promptRouter.getSystemPrompt(request)
        val config = buildConversationConfig(systemPrompt)

        val initial = getOrRecoverConversation(
            conversationId = request.conversationId,
            config = config,
            systemPrompt = systemPrompt,
            history = request.history
        )

        val managed = resetConversationIfNeeded(
            initial, request.conversationId, systemPrompt, request.history
        )

        val accumulated = StringBuilder()
        return flow {
            var toolCallName: String? = null
            var toolCallArgs: Map<String, Any> = emptyMap()

            managed.conversation.sendMessageAsync(
                    request.message,
                    extraContext = mapOf("enable_thinking" to "true")
                )
                .collect { msg ->
                    accumulated.append(msg.toString())
                    if (msg.toolCalls.isNotEmpty()) {
                        val tc = msg.toolCalls.first()
                        toolCallName = tc.name
                        @Suppress("UNCHECKED_CAST")
                        toolCallArgs = tc.arguments as? Map<String, Any> ?: emptyMap()
                    }
                    val (thinking, response) = parseThinkingAndResponse(accumulated.toString())
                    emit(StreamChunk(
                        responseText = response,
                        thinkingText = thinking
                    ))
                }

            val raw = accumulated.toString()
            val (_, response) = parseThinkingAndResponse(raw)
            managed.recordExchange(request.message.length, response.length)
            Log.d(TAG, "Chat streaming [${request.conversationId}] turn=${managed.turnCount} " +
                "est_tokens=${managed.estimatedTokens}")

            // Handle native tool calls after streaming completes
            if (toolCallName != null) {
                val parsedAction = ParsedAction(
                    functionName = toolCallName!!,
                    parameters = toolCallArgs
                )
                val actionResult = actionDispatcher.dispatch(parsedAction)
                emit(StreamChunk(
                    responseText = response,
                    thinkingText = null,
                    actionResult = actionResult
                ))
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun processVisionChatStreaming(request: AiRequest.VisionChat): Flow<StreamChunk> {
        val systemPrompt = promptRouter.getSystemPrompt(request)
        val config = buildConversationConfig(systemPrompt)

        val initial = getOrRecoverConversation(
            conversationId = request.conversationId,
            config = config,
            systemPrompt = systemPrompt,
            history = request.history
        )

        val managed = resetConversationIfNeeded(
            initial, request.conversationId, systemPrompt, request.history
        )

        val contents = Contents.of(
            Content.ImageBytes(request.imageBytes),
            Content.Text(request.message)
        )

        val accumulated = StringBuilder()
        return flow {
            var toolCallName: String? = null
            var toolCallArgs: Map<String, Any> = emptyMap()

            managed.conversation.sendMessageAsync(
                    contents,
                    extraContext = mapOf("enable_thinking" to "true")
                )
                .collect { msg ->
                    accumulated.append(msg.toString())
                    if (msg.toolCalls.isNotEmpty()) {
                        val tc = msg.toolCalls.first()
                        toolCallName = tc.name
                        @Suppress("UNCHECKED_CAST")
                        toolCallArgs = tc.arguments as? Map<String, Any> ?: emptyMap()
                    }
                    val (thinking, response) = parseThinkingAndResponse(accumulated.toString())
                    emit(StreamChunk(
                        responseText = response,
                        thinkingText = thinking
                    ))
                }

            val (_, response) = parseThinkingAndResponse(accumulated.toString())
            managed.recordExchange(request.message.length, response.length)
            Log.d(TAG, "VisionChat streaming [${request.conversationId}] turn=${managed.turnCount} " +
                "est_tokens=${managed.estimatedTokens}")

            // Handle native tool calls after streaming completes
            if (toolCallName != null) {
                val parsedAction = ParsedAction(
                    functionName = toolCallName!!,
                    parameters = toolCallArgs
                )
                val actionResult = actionDispatcher.dispatch(parsedAction)
                emit(StreamChunk(
                    responseText = response,
                    thinkingText = null,
                    actionResult = actionResult
                ))
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Route a completed chat response — tool calls are already handled at the
     * conversation level via native LiteRT-LM ToolSet, so this is purely text.
     */
    suspend fun routeChatResponse(responseText: String): AiResponse {
        return AiResponse.TextResponse(responseText)
    }

    private suspend fun processChat(request: AiRequest.TextChat): AiResponse {
        val systemPrompt = promptRouter.getSystemPrompt(request)
        val config = buildConversationConfig(systemPrompt)

        val initial = getOrRecoverConversation(
            conversationId = request.conversationId,
            config = config,
            systemPrompt = systemPrompt,
            history = request.history
        )

        val managed = resetConversationIfNeeded(
            initial, request.conversationId, systemPrompt, request.history
        )

        val result: Message = managed.conversation.sendMessage(request.message)
        val responseText = result.toString()
        managed.recordExchange(request.message.length, responseText.length)
        Log.d(TAG, "Chat [${request.conversationId}] turn=${managed.turnCount} " +
            "est_tokens=${managed.estimatedTokens}")

        // Handle native tool calls
        val toolResponse = handleToolCalls(result)
        if (toolResponse != null) return toolResponse

        return AiResponse.TextResponse(responseText)
    }

    /**
     * Process a vision request within the persistent chat conversation.
     * The image + text are sent together so the model has full conversation
     * context.
     */
    private suspend fun processVisionChat(request: AiRequest.VisionChat): AiResponse {
        val systemPrompt = promptRouter.getSystemPrompt(request)
        val config = buildConversationConfig(systemPrompt)

        val initial = getOrRecoverConversation(
            conversationId = request.conversationId,
            config = config,
            systemPrompt = systemPrompt,
            history = request.history
        )

        val managed = resetConversationIfNeeded(
            initial, request.conversationId, systemPrompt, request.history
        )

        val message = Contents.of(
            Content.ImageBytes(request.imageBytes),
            Content.Text(request.message)
        )
        val result: Message = managed.conversation.sendMessage(message)
        val responseText = result.toString()
        managed.recordExchange(request.message.length, responseText.length)
        Log.d(TAG, "VisionChat [${request.conversationId}] turn=${managed.turnCount} " +
            "est_tokens=${managed.estimatedTokens}")

        // Handle native tool calls first
        val toolResponse = handleToolCalls(result)
        if (toolResponse != null) return toolResponse

        return routeVisionChatResponse(responseText, request.message)
    }

    /**
     * Extract and dispatch native tool calls from a LiteRT-LM Message.
     * Returns an [AiResponse.ActionResponse] if a tool was called, null otherwise.
     */
    private suspend fun handleToolCalls(message: Message): AiResponse? {
        if (message.toolCalls.isEmpty()) return null
        val toolCall = message.toolCalls.first()
        @Suppress("UNCHECKED_CAST")
        val parsedAction = ParsedAction(
            functionName = toolCall.name,
            parameters = toolCall.arguments as? Map<String, Any> ?: emptyMap()
        )
        val actionResult = actionDispatcher.dispatch(parsedAction)
        return AiResponse.ActionResponse(actionResult)
    }

    /**
     * Route a vision-chat response through format-based parsing:
     * 1. ```json code fence → detection parse (box_2d + label)
     * 2. Plain text fallback (captioning, VQA, OCR — all natural language)
     *
     * Tool calls are handled upstream via native LiteRT-LM ToolSet.
     */
    suspend fun routeVisionChatResponse(responseText: String, userMessage: String): AiResponse {
        // 1. Try to extract ```json code fence (Google's official pattern)
        val jsonMatch = Regex("""```json\s+(.*?)\s+```""", RegexOption.DOT_MATCHES_ALL)
            .find(responseText)

        if (jsonMatch != null) {
            val jsonStr = jsonMatch.groupValues[1]
            val detections = detectionParser.parse(jsonStr)
            if (detections.isNotEmpty()) {
                Log.d(TAG, "VisionChat detection parsed: ${detections.size} objects")
                return AiResponse.DetectionResponse(detections)
            }
        }

        // 2. Plain text response (captioning, VQA, OCR — all natural language)
        val cleanText = responseText
            .replace(Regex("""```\w*\s*"""), "")
            .replace(Regex("""```\s*"""), "")
            .trim()
        return AiResponse.TextResponse(cleanText)
    }

    /**
     * Get the persistent conversation or recover it from history if the service
     * was restarted (process death) and the conversation was lost.
     */
    private fun getOrRecoverConversation(
        conversationId: String,
        config: ConversationConfig,
        systemPrompt: String,
        history: List<ChatMessage>
    ): ManagedConversation {
        // Fast path: conversation already exists in memory
        val existing = try {
            engine.getOrCreateConversation(conversationId, config, systemPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get/create conversation", e)
            throw e
        }

        // If this is a brand-new conversation but we have prior history,
        // rebuild it with initialMessages so the model has context
        if (existing.turnCount == 0 && history.isNotEmpty()) {
            val priorHistory = history.dropLast(1) // Exclude the message about to be sent
                .filter { it.role != ChatMessage.Role.SYSTEM }
            if (priorHistory.isNotEmpty()) {
                Log.i(TAG, "Recovering conversation $conversationId with " +
                    "${priorHistory.size} prior messages")
                val recentHistory = priorHistory.takeLast(RECENT_HISTORY_KEEP_COUNT)
                val recoveryConfig = buildConfigWithHistory(systemPrompt, recentHistory)
                val recovered = engine.resetConversation(conversationId, recoveryConfig, systemPrompt)
                recovered.recordSystemPrompt(systemPrompt.length)
                recentHistory.forEach { msg ->
                    recovered.recordExchange(
                        if (msg.role == ChatMessage.Role.USER) msg.content.length else 0,
                        if (msg.role == ChatMessage.Role.ASSISTANT) msg.content.length else 0
                    )
                }
                return recovered
            }
        }

        if (existing.turnCount == 0) {
            existing.recordSystemPrompt(systemPrompt.length)
        }
        return existing
    }

    /**
     * Reset the conversation with recent history if the estimated token count
     * exceeds [CONTEXT_RESET_THRESHOLD]. Returns the original [managed] if no
     * reset is needed.
     */
    private suspend fun resetConversationIfNeeded(
        managed: ManagedConversation,
        conversationId: String,
        systemPrompt: String,
        history: List<ChatMessage>
    ): ManagedConversation {
        if (managed.estimatedTokens <= CONTEXT_RESET_THRESHOLD) return managed
        Log.i(TAG, "Context approaching limit (${managed.estimatedTokens} est. tokens). " +
            "Resetting conversation $conversationId with recent history.")
        val recentHistory = history.takeLast(RECENT_HISTORY_KEEP_COUNT)
        val resetConfig = buildConfigWithHistory(systemPrompt, recentHistory)
        val resetManaged = engine.resetConversation(conversationId, resetConfig, systemPrompt)
        resetManaged.recordSystemPrompt(systemPrompt.length)
        recentHistory.forEach { msg ->
            resetManaged.recordExchange(
                if (msg.role == ChatMessage.Role.USER) msg.content.length else 0,
                if (msg.role == ChatMessage.Role.ASSISTANT) msg.content.length else 0
            )
        }
        return resetManaged
    }

    /**
     * Build a [ConversationConfig] with native ToolSet registration.
     * All tool definitions come from [GemmaToolSet]; automatic execution
     * is disabled so we can check permissions before dispatching.
     */
    private fun buildConversationConfig(
        systemPrompt: String,
        initialMessages: List<Message> = emptyList()
    ): ConversationConfig {
        return if (initialMessages.isNotEmpty()) {
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                tools = listOf(tool(gemmaToolSet)),
                automaticToolCalling = false,
                initialMessages = initialMessages,
                channels = listOf(Channel("thinking", "<think>", "</think>")),
                samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0)
            )
        } else {
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                tools = listOf(tool(gemmaToolSet)),
                automaticToolCalling = false,
                channels = listOf(Channel("thinking", "<think>", "</think>")),
                samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0)
            )
        }
    }

    /**
     * Build a [ConversationConfig] seeded with recent history via initialMessages.
     */
    private fun buildConfigWithHistory(
        systemPrompt: String,
        history: List<ChatMessage>
    ): ConversationConfig {
        val initialMessages = history.mapNotNull { msg ->
            when (msg.role) {
                ChatMessage.Role.USER -> Message.user(msg.content)
                ChatMessage.Role.ASSISTANT -> Message.model(msg.content)
                ChatMessage.Role.SYSTEM -> null
            }
        }
        return buildConversationConfig(systemPrompt, initialMessages)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GemOfGemma AI",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI engine running in background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String = "AI engine active"): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("GemOfGemma")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private suspend fun initializeEngine() {
        try {
            engine.initialize(
                modelPath = modelDownloadManager.getModelPath(),
                cacheDir = cacheDir.path
            )
            _isEngineReady.value = true
            updateNotification("AI engine active")
            Log.i(TAG, "GemmaService ready")
        } catch (e: Exception) {
            _isEngineReady.value = false
            Log.e(TAG, "Failed to initialize engine", e)
            updateNotification("Engine initialization failed")
        }
    }

    private fun updateNotification(text: String, progress: Int = -1) {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("GemOfGemma")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, builder.build())
    }

    /** Explicitly reset/clear a chat conversation (e.g., user presses "New Chat"). */
    fun resetChat(conversationId: String) {
        engine.closeConversation(conversationId)
        Log.i(TAG, "Chat conversation reset: $conversationId")
    }

    private fun formatSize(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 0.1) "%.2f GB".format(gb) else "%.0f MB".format(bytes / 1_048_576.0)
    }

    companion object {
        private const val TAG = "GemmaService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gemofgemma_ai_channel"

        /**
         * Estimated token threshold before auto-resetting the conversation with
         * recent history. Gemma 4 E2B supports 32K tokens; we reset at ~6K to
         * leave ample room for the new exchange and response.
         */
        private const val CONTEXT_RESET_THRESHOLD = 6_000

        /** Number of recent messages to keep when rebuilding after a context reset. */
        private const val RECENT_HISTORY_KEEP_COUNT = 20

        /**
         * Parse raw streamed text to separate thinking from response.
         * Handles both `<think>...</think>` (LiteRT-LM channel markers) and
         * `<|channel>thought...\n<channel|>` (raw Gemma 4 format).
         */
        fun parseThinkingAndResponse(rawText: String): Pair<String?, String> {
            // Try LiteRT-LM channel markers first
            for ((start, end) in listOf(
                "<think>" to "</think>",
                "<|channel>thought" to "<channel|>"
            )) {
                val startIdx = rawText.indexOf(start)
                if (startIdx == -1) continue

                val contentStart = startIdx + start.length
                val endIdx = rawText.indexOf(end, contentStart)
                if (endIdx == -1) {
                    // Still thinking — no response yet
                    val thinking = rawText.substring(contentStart).trimStart('\n')
                    return Pair(thinking.ifEmpty { null }, "")
                }
                // Both present — split
                val thinking = rawText.substring(contentStart, endIdx).trim()
                val response = rawText.substring(endIdx + end.length).trimStart('\n')
                return Pair(thinking.ifEmpty { null }, response)
            }
            // No thinking markers found — entire text is response
            return Pair(null, rawText)
        }
    }
}

