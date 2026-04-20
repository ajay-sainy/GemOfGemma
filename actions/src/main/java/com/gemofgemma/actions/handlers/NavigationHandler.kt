package com.gemofgemma.actions.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val destination = params["destination"] as? String
            ?: return ActionResult.Error("Missing destination parameter")

        return try {
            val geoUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to generic geo intent if Google Maps not installed
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
            ActionResult.Success("Navigating to $destination")
        } catch (e: Exception) {
            ActionResult.Error("Failed to start navigation: ${e.message}", e)
        }
    }
}
