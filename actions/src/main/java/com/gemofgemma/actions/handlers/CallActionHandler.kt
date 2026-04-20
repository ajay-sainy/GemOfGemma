package com.gemofgemma.actions.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val phoneNumber = params["phoneNumber"] as? String
            ?: return ActionResult.Error("Missing phoneNumber parameter")

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Calling $phoneNumber")
        } catch (e: SecurityException) {
            ActionResult.PermissionRequired(listOf(Manifest.permission.CALL_PHONE))
        } catch (e: Exception) {
            ActionResult.Error("Failed to make call: ${e.message}", e)
        }
    }
}
