package com.gemofgemma.actions.handlers

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val action = params["action"] as? String
            ?: return ActionResult.Error("Missing action parameter")

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val keyCode = when (action.lowercase()) {
                "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
                "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> return ActionResult.Error("Unknown media action: $action")
            }
            dispatchMediaKey(audioManager, keyCode)
            ActionResult.Success("Media: $action")
        } catch (e: Exception) {
            ActionResult.Error("Failed to control media: ${e.message}", e)
        }
    }

    private fun dispatchMediaKey(audioManager: AudioManager, keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}
