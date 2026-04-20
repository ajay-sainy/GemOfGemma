package com.gemofgemma.core

import com.gemofgemma.core.model.AiRequest
import com.gemofgemma.core.model.AiResponse
import com.gemofgemma.core.model.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the AI pipeline. Implemented by GemmaServiceConnector
 * in :ai module. ViewModels in :ui inject this interface to avoid depending
 * on the concrete service/engine classes.
 */
interface AiProcessor {
    suspend fun process(request: AiRequest): AiResponse

    /**
     * Stream tokens for an AI request. Each emission is a [StreamChunk]
     * containing both the accumulated response text and any thinking/reasoning text.
     * The final emission is the complete response. Only supports TextChat and VisionChat.
     */
    suspend fun processStreaming(request: AiRequest): Flow<StreamChunk>

    /**
     * Post-process a completed streamed response for a TextChat request.
     * Handles function call parsing and action dispatch.
     */
    suspend fun postProcessChat(responseText: String): AiResponse

    /**
     * Post-process a completed streamed response for a VisionChat request.
     * Handles detection/OCR parsing, function calls, etc.
     */
    suspend fun postProcessVisionChat(responseText: String, userMessage: String): AiResponse

    /** Whether the model file is on disk and ready. */
    val isModelAvailable: StateFlow<Boolean>

    /** Whether the AI engine is initialized and ready to process requests. */
    val isEngineReady: StateFlow<Boolean>

    /** 0f..1f progress of the current download. */
    val downloadProgress: StateFlow<Float>

    /** True while a download is in progress. */
    val isDownloading: StateFlow<Boolean>

    /** Bytes downloaded so far during the current/last download. */
    val downloadedBytes: StateFlow<Long>

    /** Total bytes expected for the model file. */
    val totalBytes: StateFlow<Long>

    /** Start or resume the model download. */
    suspend fun downloadModel(): Result<Unit>

    /** Delete the model from disk. */
    suspend fun deleteModel(): Result<Unit>

    /** Size in bytes of the model on disk (0 if not downloaded). */
    fun getModelSizeOnDisk(): Long

    /** True if a partial .tmp file exists from a previous interrupted download. */
    fun hasPartialDownload(): Boolean

    /** Reset a chat conversation, clearing all context. */
    suspend fun resetChat(conversationId: String)

    /** Cancel any in-progress generation. */
    suspend fun cancelGeneration()
}
