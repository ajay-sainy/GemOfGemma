package com.gemofgemma.actions.handlers

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AmbientNoiseHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(): ActionResult {
        return withContext(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                    return@withContext ActionResult.Error("Invalid audio configuration on this device")
                }

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    return@withContext ActionResult.Error("Could not initialize audio recording")
                }

                recorder.startRecording()

                val buffer = ShortArray(bufferSize / 2)
                val read = recorder.read(buffer, 0, buffer.size)

                recorder.stop()

                if (read <= 0) {
                    return@withContext ActionResult.Error("No audio data captured")
                }

                var sum = 0.0
                for (i in 0 until read) {
                    sum += buffer[i].toDouble() * buffer[i].toDouble()
                }
                val rms = sqrt(sum / read)
                val db = if (rms > 0) 20 * log10(rms / Short.MAX_VALUE) + 90 else 0.0

                val description = when {
                    db < 40 -> "quiet"
                    db < 70 -> "moderate"
                    else -> "loud"
                }

                ActionResult.Success("Ambient noise: ${"%.1f".format(db)} dB ($description)")
            } catch (e: SecurityException) {
                ActionResult.Error("Microphone permission not granted", e)
            } catch (e: Exception) {
                ActionResult.Error("Failed to measure ambient noise: ${e.message}", e)
            } finally {
                try {
                    recorder?.release()
                } catch (_: Exception) {}
            }
        }
    }
}
