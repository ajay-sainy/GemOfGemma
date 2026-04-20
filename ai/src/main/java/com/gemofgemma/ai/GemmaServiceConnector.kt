package com.gemofgemma.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.gemofgemma.ai.model.ModelDownloadManager
import com.gemofgemma.core.AiProcessor
import com.gemofgemma.core.model.AiRequest
import com.gemofgemma.core.model.AiResponse
import com.gemofgemma.core.model.StreamChunk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton connector that binds to [GemmaService] and exposes a suspend
 * [process] method for ViewModels. Handles the ServiceConnection lifecycle
 * and gracefully waits for the service to become available.
 *
 * Also delegates model management (download / delete / status) to
 * [ModelDownloadManager] so the UI layer can work through [AiProcessor]
 * without depending on :ai directly.
 */
@Singleton
class GemmaServiceConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) : AiProcessor {

    private val _service = MutableStateFlow<GemmaService?>(null)

    private val _isEngineReady = MutableStateFlow(false)
    override val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private var serviceScope: CoroutineScope? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as GemmaService.GemmaBinder).getService()
            _service.value = service
            // Forward engine readiness state from the service
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            serviceScope = scope
            scope.launch {
                service.isEngineReady.collect { ready ->
                    _isEngineReady.value = ready
                }
            }
            Log.i(TAG, "Bound to GemmaService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            _isEngineReady.value = false
            serviceScope?.cancel()
            serviceScope = null
            Log.w(TAG, "GemmaService disconnected")
        }
    }

    init {
        val intent = Intent(context, GemmaService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    // ── AI processing ────────────────────────────────────────────

    override suspend fun process(request: AiRequest): AiResponse {
        val service = withTimeoutOrNull(15_000L) {
            _service.filterNotNull().first()
        } ?: return AiResponse.ErrorResponse(
            "AI service not available. Model may still be loading — please try again shortly."
        )
        return service.process(request)
    }

    override suspend fun processStreaming(request: AiRequest): Flow<StreamChunk> {
        val service = _service.value
            ?: throw IllegalStateException("AI service not available yet.")
        return service.processStreaming(request)
    }

    override suspend fun postProcessChat(responseText: String): AiResponse {
        val service = withTimeoutOrNull(15_000L) {
            _service.filterNotNull().first()
        } ?: return AiResponse.ErrorResponse("AI service not available.")
        return service.routeChatResponse(responseText)
    }

    override suspend fun postProcessVisionChat(responseText: String, userMessage: String): AiResponse {
        val service = withTimeoutOrNull(15_000L) {
            _service.filterNotNull().first()
        } ?: return AiResponse.ErrorResponse("AI service not available.")
        return service.routeVisionChatResponse(responseText, userMessage)
    }

    // ── Model management (delegates to ModelDownloadManager) ─────

    override val isModelAvailable: StateFlow<Boolean>
        get() = modelDownloadManager.isModelAvailableFlow

    override val downloadProgress: StateFlow<Float>
        get() = modelDownloadManager.downloadProgress

    override val isDownloading: StateFlow<Boolean>
        get() = modelDownloadManager.isDownloading

    override val downloadedBytes: StateFlow<Long>
        get() = modelDownloadManager.downloadedBytes

    override val totalBytes: StateFlow<Long>
        get() = modelDownloadManager.totalBytes

    override suspend fun downloadModel(): Result<Unit> =
        modelDownloadManager.downloadModel().map { }

    override suspend fun deleteModel(): Result<Unit> =
        modelDownloadManager.deleteModel()

    override fun getModelSizeOnDisk(): Long =
        modelDownloadManager.getModelSizeOnDisk()

    override fun hasPartialDownload(): Boolean =
        modelDownloadManager.hasPartialDownload()

    override suspend fun resetChat(conversationId: String) {
        val service = withTimeoutOrNull(15_000L) {
            _service.filterNotNull().first()
        } ?: return
        service.resetChat(conversationId)
    }

    override suspend fun cancelGeneration() {
        // Cancellation is handled by the ViewModel cancelling the coroutine Job.
        // The streaming flow (flowOn Dispatchers.IO) will be cancelled cooperatively.
    }

    companion object {
        private const val TAG = "GemmaServiceConnector"
    }
}
