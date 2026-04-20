package com.gemofgemma.ai

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around LiteRT-LM Engine that handles initialization, backend
 * selection (GPU with CPU fallback), and conversation lifecycle.
 *
 * IMPORTANT: LiteRT-LM only supports ONE conversation at a time per engine.
 * All methods that create conversations must close the previous one first.
 */
@Singleton
class GemmaEngine @Inject constructor() {

    private var engine: Engine? = null

    /** The single active conversation — LiteRT-LM only supports one at a time. */
    private var activeConversation: ManagedConversation? = null
    private val lock = Any()

    @Volatile
    var isInitialized: Boolean = false
        private set

    @Volatile
    var initError: String? = null
        private set

    suspend fun initialize(modelPath: String, cacheDir: String) {
        withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    initError = "Model file not found: $modelPath"
                    Log.e(TAG, initError!!)
                    return@withContext
                }

                try {
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        cacheDir = cacheDir
                    )
                    engine = Engine(config).also { it.initialize() }
                    isInitialized = true
                    initError = null
                    Log.i(TAG, "GemmaEngine initialized with CPU backend")      
                } catch (gpuEx: Exception) {
                    Log.w(TAG, "CPU init failed due to an exception", gpuEx)
                    try {
                        val cpuConfig = EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.CPU(),
                            visionBackend = Backend.CPU(),
                            cacheDir = cacheDir
                        )
                        engine = Engine(cpuConfig).also { it.initialize() }
                        isInitialized = true
                        initError = null
                        Log.i(TAG, "GemmaEngine initialized with CPU backend (fallback)")
                    } catch (cpuEx: Exception) {
                        initError = "Both GPU and CPU initialization failed: ${cpuEx.message}"
                        Log.e(TAG, initError!!, cpuEx)
                    }
                }
            } catch (e: Exception) {
                initError = "Engine initialization failed: ${e.message}"
                Log.e(TAG, initError!!, e)
            }
        }
    }

    /**
     * Get the active conversation if it matches [conversationId], or create a new one.
     * Closes any existing conversation first (LiteRT-LM only supports one at a time).
     */
    fun getOrCreateConversation(
        conversationId: String,
        config: ConversationConfig, systemPrompt: String
    ): ManagedConversation {
        synchronized(lock) {
            val existing = activeConversation
            if (existing != null && existing.id == conversationId) { 
                    if (existing.systemPrompt == systemPrompt) { 
                        return existing 
                    } else { 
                        android.util.Log.i(TAG, "System prompt changed. Resetting conversation.") 
                        return resetConversation(conversationId, config, systemPrompt) 
                    }
                }
            // Close any existing conversation before creating a new one
            closeActiveConversation()
            val eng = engine ?: throw IllegalStateException(
                initError ?: "Engine not initialized. Call initialize() first."
            )
            val conversation = eng.createConversation(config)
            val managed = ManagedConversation(conversation, conversationId, systemPrompt)
            activeConversation = managed
            Log.i(TAG, "Created conversation: $conversationId")
            return managed
        }
    }

    /**
     * Reset the active conversation — closes it and creates a fresh one.
     * Used when context window fills up or user explicitly resets.
     */
    fun resetConversation(
        conversationId: String,
        config: ConversationConfig, systemPrompt: String
    ): ManagedConversation {
        synchronized(lock) {
            closeActiveConversation()
            val eng = engine ?: throw IllegalStateException(
                initError ?: "Engine not initialized. Call initialize() first."
            )
            val conversation = eng.createConversation(config)
            val managed = ManagedConversation(conversation, conversationId, systemPrompt)
            activeConversation = managed
            Log.i(TAG, "Reset conversation: $conversationId")
            return managed
        }
    }

    /** Close and remove a specific conversation. */
    fun closeConversation(conversationId: String) {
        synchronized(lock) {
            val active = activeConversation
            if (active != null && active.id == conversationId) {
                closeActiveConversation()
            }
        }
    }

    /**
     * Create a one-shot conversation for vision/voice requests.
     * Closes any active conversation first (LiteRT-LM limit).
     * The caller is responsible for closing it when done.
     */
    fun createOneShotConversation(config: ConversationConfig): Conversation {
        synchronized(lock) {
            closeActiveConversation()
            val eng = engine ?: throw IllegalStateException(
                initError ?: "Engine not initialized. Call initialize() first."
            )
            return eng.createConversation(config)
        }
    }

    /** Close the active conversation if one exists. */
    private fun closeActiveConversation() {
        activeConversation?.let { managed ->
            try {
                managed.conversation.close()
                Log.i(TAG, "Closed conversation: ${managed.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing conversation ${managed.id}", e)
            }
        }
        activeConversation = null
    }

    fun release() {
        synchronized(lock) {
            closeActiveConversation()
            engine?.close()
            engine = null
            isInitialized = false
            initError = null
            Log.i(TAG, "GemmaEngine released")
        }
    }

    companion object {
        private const val TAG = "GemmaEngine"
    }
}

/**
 * Wraps a LiteRT-LM [Conversation] with token-usage tracking for context
 * management. The conversation maintains its own internal history — calling
 * [Conversation.sendMessage] appends to it automatically.
 */
class ManagedConversation(
    val conversation: Conversation,
    val id: String, val systemPrompt: String
) {
    /** Estimated token count for all messages in this conversation. */
    @Volatile
    var estimatedTokens: Int = 0
        private set

    /** Number of user-model exchange turns. */
    @Volatile
    var turnCount: Int = 0
        private set

    /** Record token usage after a user message + model response exchange. */
    fun recordExchange(userMessageChars: Int, responseChars: Int) {
        // Rough estimate: ~1 token per 4 characters for Gemma's SentencePiece tokenizer
        val userTokens = (userMessageChars + 3) / 4
        val responseTokens = (responseChars + 3) / 4
        estimatedTokens += userTokens + responseTokens
        turnCount++
    }

    /** Record initial system prompt token usage. */
    fun recordSystemPrompt(promptChars: Int) {
        estimatedTokens += (promptChars + 3) / 4
    }

    companion object {
        private const val TAG = "ManagedConversation"
    }
}

