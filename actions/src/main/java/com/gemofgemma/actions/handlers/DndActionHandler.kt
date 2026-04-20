package com.gemofgemma.actions.handlers

import android.app.NotificationManager
import android.content.Context
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DndActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val enabled = params["enabled"] as? Boolean
            ?: return ActionResult.Error("Missing 'enabled' parameter")

        return try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (!notificationManager.isNotificationPolicyAccessGranted) {
                return ActionResult.PermissionRequired(
                    listOf("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS")
                )
            }

            val filter = if (enabled) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            notificationManager.setInterruptionFilter(filter)
            ActionResult.Success("Do Not Disturb ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            ActionResult.Error("Failed to toggle DND: ${e.message}", e)
        }
    }
}
