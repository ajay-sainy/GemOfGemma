package com.gemofgemma.actions

import com.gemofgemma.actions.model.ParsedAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates actions against safety rules before execution.
 * Blocks dangerous actions entirely and flags sensitive ones for user confirmation.
 */
@Singleton
class SafetyValidator @Inject constructor() {

    sealed class ValidationResult {
        data object Approved : ValidationResult()
        data object Blocked : ValidationResult()
        data class NeedsConfirmation(val reason: String) : ValidationResult()
    }

    private val blockedActions = setOf(
        "wipeData",
        "factoryReset",
        "installApp",
        "uninstallApp",
        "grantPermission",
        "revokePermission",
    )

    private val confirmationRequiredActions = setOf(
        "sendSms",
        "makeCall",
        "createCalendarEvent",
        "navigate",
    )

    fun validate(action: ParsedAction): ValidationResult {
        if (action.functionName in blockedActions) {
            return ValidationResult.Blocked
        }
        if (action.functionName in confirmationRequiredActions) {
            return ValidationResult.NeedsConfirmation(
                buildConfirmationMessage(action)
            )
        }
        return ValidationResult.Approved
    }

    private fun buildConfirmationMessage(action: ParsedAction): String {
        return when (action.functionName) {
            "sendSms" -> {
                val to = action.parameters["phoneNumber"] ?: "unknown"
                val msg = action.parameters["message"] ?: ""
                "Send SMS to $to: \"$msg\"?"
            }
            "makeCall" -> {
                val to = action.parameters["phoneNumber"] ?: "unknown"
                "Call $to?"
            }
            "createCalendarEvent" -> {
                val title = action.parameters["title"] ?: "event"
                "Create calendar event: \"$title\"?"
            }
            "navigate" -> {
                val dest = action.parameters["destination"] ?: "unknown"
                "Navigate to $dest?"
            }
            else -> "Execute ${action.functionName}?"
        }
    }
}
