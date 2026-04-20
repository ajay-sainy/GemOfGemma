package com.gemofgemma.ai.model

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles downloading Gemma 4 E2B model from HuggingFace if not cached locally.
 * Supports resume after interrupted downloads via HTTP Range header.
 * The model file (~2.58 GB) is stored in the app's internal files directory.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes

    private val _totalBytes = MutableStateFlow(MODEL_SIZE_BYTES)
    val totalBytes: StateFlow<Long> = _totalBytes

    private val _isModelAvailable = MutableStateFlow(false)
    val isModelAvailableFlow: StateFlow<Boolean> = _isModelAvailable

    // Store in app-internal storage — no external storage permission needed
    private val modelDir: File = File(context.filesDir, "models").also { it.mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILENAME)
    private val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")

    init {
        _isModelAvailable.value = modelFile.exists() && modelFile.length() > 0
    }

    fun isModelAvailable(): Boolean = modelFile.exists() && modelFile.length() > 0

    fun getModelPath(): String = modelFile.absolutePath

    fun getModelSizeOnDisk(): Long = if (modelFile.exists()) modelFile.length() else 0L

    fun hasPartialDownload(): Boolean = tempFile.exists() && tempFile.length() > 0

    suspend fun downloadModel(): Result<File> = withContext(Dispatchers.IO) {
        if (isModelAvailable()) {
            _isModelAvailable.value = true
            return@withContext Result.success(modelFile)
        }

        if (_isDownloading.value) {
            return@withContext Result.failure(Exception("Download already in progress"))
        }

        _isDownloading.value = true
        _downloadProgress.value = 0f

        try {
            modelDir.mkdirs()

            // Resume support: reuse bytes already in temp file
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            val requestBuilder = Request.Builder().url(MODEL_URL)
            if (existingBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            // 206 = Partial Content (resume), 200 = full download
            if (!response.isSuccessful && response.code != 206) {
                return@withContext Result.failure(
                    Exception("Download failed: HTTP ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                Exception("Empty response body")
            )

            val contentLength = body.contentLength()
            val totalSize = if (response.code == 206) {
                existingBytes + contentLength
            } else {
                if (contentLength > 0) contentLength else MODEL_SIZE_BYTES
            }
            _totalBytes.value = totalSize

            var bytesRead = if (response.code == 206) existingBytes else 0L
            _downloadedBytes.value = bytesRead
            if (totalSize > 0) {
                _downloadProgress.value = bytesRead.toFloat() / totalSize
            }

            val append = response.code == 206 && existingBytes > 0

            body.byteStream().use { input ->
                FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(65_536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        _downloadedBytes.value = bytesRead
                        if (totalSize > 0) {
                            _downloadProgress.value = bytesRead.toFloat() / totalSize
                        }
                    }
                }
            }

            if (!tempFile.renameTo(modelFile)) {
                return@withContext Result.failure(Exception("Failed to finalize model file"))
            }

            _downloadProgress.value = 1f
            _isModelAvailable.value = true
            Log.i(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")
            Result.success(modelFile)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            Result.failure(e)
        } finally {
            _isDownloading.value = false
        }
    }

    suspend fun deleteModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tempFile.delete()
            if (modelFile.exists()) {
                if (modelFile.delete()) {
                    _isModelAvailable.value = false
                    _downloadProgress.value = 0f
                    _downloadedBytes.value = 0L
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete model file"))
                }
            } else {
                _isModelAvailable.value = false
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_SIZE_BYTES = 2_770_000_000L
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    }
}
