package com.gemofgemma.actions.handlers

import android.Manifest
import android.content.Context
import android.telephony.SmsManager
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val phoneNumber = params["phoneNumber"] as? String
            ?: return ActionResult.Error("Missing phoneNumber parameter")
        val message = params["message"] as? String
            ?: return ActionResult.Error("Missing message parameter")

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            ActionResult.Success("SMS sent to $phoneNumber")
        } catch (e: SecurityException) {
            ActionResult.PermissionRequired(listOf(Manifest.permission.SEND_SMS))
        } catch (e: Exception) {
            ActionResult.Error("Failed to send SMS: ${e.message}", e)
        }
    }
}
