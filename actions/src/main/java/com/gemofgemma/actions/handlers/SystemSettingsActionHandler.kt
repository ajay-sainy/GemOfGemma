package com.gemofgemma.actions.handlers

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemSettingsActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun executeSetStreamVolume(params: Map<String, Any>): ActionResult {
        val percent = (params["percent"] as? Number)?.toInt()
            ?: return ActionResult.Error("Missing percent parameter")

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (percent / 100f * maxVolume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            ActionResult.Success("Volume set to $percent%")
        } catch (e: Exception) {
            ActionResult.Error("Failed to set volume: ${e.message}", e)
        }
    }

    suspend fun executeToggleFlashlight(params: Map<String, Any>): ActionResult {
        val on = (params["on"] as? Boolean) ?: return ActionResult.Error("Missing 'on' boolean parameter")
        
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return ActionResult.Error("No camera found")
            cameraManager.setTorchMode(cameraId, on)
            ActionResult.Success("Flashlight turned ${if (on) "on" else "off"}")
        } catch (e: Exception) {
            ActionResult.Error("Failed to toggle flashlight: ${e.message}", e)
        }
    }
}
