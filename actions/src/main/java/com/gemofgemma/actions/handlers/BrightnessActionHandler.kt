package com.gemofgemma.actions.handlers

import android.content.Context
import android.provider.Settings
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrightnessActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val percent = (params["percent"] as? Number)?.toInt()
            ?: return ActionResult.Error("Missing percent parameter")

        return try {
            if (!Settings.System.canWrite(context)) {
                return ActionResult.PermissionRequired(
                    listOf("android.settings.action.MANAGE_WRITE_SETTINGS")
                )
            }

            // Disable auto-brightness first
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            // Set brightness (0-255 range)
            val brightness = (percent / 100f * 255).toInt().coerceIn(1, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
            ActionResult.Success("Brightness set to $percent%")
        } catch (e: Exception) {
            ActionResult.Error("Failed to set brightness: ${e.message}", e)
        }
    }
}
