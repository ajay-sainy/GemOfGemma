package com.gemofgemma.actions.handlers

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioHapticsActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun executePlayHaptic(params: Map<String, Any>): ActionResult {
        return try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (!vibrator.hasVibrator()) {
                return ActionResult.Error("Device does not have a vibrator")
            }

            // Simple pattern, e.g. morse code or a single strong buzz
            val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
            
            ActionResult.Success("Played haptic pattern")
        } catch (e: Exception) {
            ActionResult.Error("Failed to play haptic pattern: ${e.message}", e)
        }
    }
}
