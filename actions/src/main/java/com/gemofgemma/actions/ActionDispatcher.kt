package com.gemofgemma.actions

import com.gemofgemma.actions.handlers.AlarmActionHandler
import com.gemofgemma.actions.handlers.AppLaunchHandler
import com.gemofgemma.actions.handlers.BrightnessActionHandler
import com.gemofgemma.actions.handlers.CalendarActionHandler
import com.gemofgemma.actions.handlers.CallActionHandler
import com.gemofgemma.actions.handlers.DndActionHandler
import com.gemofgemma.actions.handlers.MediaActionHandler
import com.gemofgemma.actions.handlers.NavigationHandler
import com.gemofgemma.actions.handlers.SearchActionHandler
import com.gemofgemma.actions.handlers.SmsActionHandler
import com.gemofgemma.actions.handlers.TelemetryActionHandler
import com.gemofgemma.actions.handlers.SystemSettingsActionHandler
import com.gemofgemma.actions.handlers.AmbientLightHandler
import com.gemofgemma.actions.handlers.AmbientNoiseHandler
import com.gemofgemma.actions.handlers.AudioHapticsActionHandler
import com.gemofgemma.actions.handlers.ClipboardHandler
import com.gemofgemma.actions.handlers.MotionStateHandler
import com.gemofgemma.actions.model.ParsedAction
import com.gemofgemma.core.data.ToolPreferencesRepository
import com.gemofgemma.core.model.ActionResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ActionDispatcher @Inject constructor(
    private val smsHandler: SmsActionHandler,
    private val callHandler: CallActionHandler,
    private val alarmHandler: AlarmActionHandler,
    private val systemSettingsHandler: SystemSettingsActionHandler,
    private val dndHandler: DndActionHandler,
    private val appLaunchHandler: AppLaunchHandler,
    private val navigationHandler: NavigationHandler,
    private val mediaHandler: MediaActionHandler,
    private val brightnessHandler: BrightnessActionHandler,
    private val calendarHandler: CalendarActionHandler,
    private val searchHandler: SearchActionHandler,
    private val telemetryHandler: TelemetryActionHandler,
    private val audioHapticsHandler: AudioHapticsActionHandler,
    private val ambientLightHandler: AmbientLightHandler,
    private val motionStateHandler: MotionStateHandler,
    private val ambientNoiseHandler: AmbientNoiseHandler,
    private val clipboardHandler: ClipboardHandler,
    private val safetyValidator: SafetyValidator,
    private val toolPreferencesRepository: ToolPreferencesRepository
) {

    suspend fun dispatch(action: ParsedAction): ActionResult {
        val validation = safetyValidator.validate(action)

        return when (validation) {
            is SafetyValidator.ValidationResult.Blocked -> {
                ActionResult.Error("Action '" + action.functionName + "' is blocked for safety reasons")
            }
            is SafetyValidator.ValidationResult.NeedsConfirmation -> {
                ActionResult.NeedsConfirmation(
                    actionDescription = validation.reason,
                    onConfirm = { /* Caller should use dispatchConfirmed() instead */ }
                )
            }
            is SafetyValidator.ValidationResult.Approved -> {
                executeActionWithOptInCheck(action)
            }
        }
    }

    suspend fun dispatchConfirmed(action: ParsedAction): ActionResult {
        return executeActionWithOptInCheck(action)
    }

    private suspend fun executeActionWithOptInCheck(action: ParsedAction): ActionResult {
        val enabledTools = toolPreferencesRepository.enabledToolsFlow.first()
        val mappedToolId = getToolIdForFunction(action.functionName)
        
        if (mappedToolId != null && !enabledTools.contains(mappedToolId)) {
            return ActionResult.Error("Access Denied: The user has not enabled the tool. You cannot use it.")
        }

        return executeAction(action)
    }

    private fun getToolIdForFunction(funcName: String): String? {
        return when (funcName) {
            "sendSms", "send_sms" -> "sendSms"
            "makeCall", "make_call" -> "makeCall"
            "setAlarm", "set_alarm" -> "setAlarm"
            "setTimer", "set_timer" -> "setTimer"
            "setVolume", "set_volume", "set_stream_volume" -> "setVolume"
            "toggleFlashlight", "toggle_flashlight" -> "toggleFlashlight"
            "toggleDnd", "toggle_dnd" -> "toggleDnd"
            "openApp", "open_app" -> "openApp"
            "navigate" -> "navigate"
            "mediaControl", "media_control" -> "mediaControl"
            "setBrightness", "set_brightness" -> "setBrightness"
            "createCalendarEvent", "create_calendar_event" -> "createCalendarEvent"
            "searchInternet", "search_internet" -> "searchInternet"
            "batteryDiagnostics", "battery_diagnostics" -> "battery_diagnostics"
            "networkInfo", "network_info" -> "network_info"
            "storageMemory", "storage_memory" -> "storage_memory"
            "playHaptic", "play_haptic" -> "play_haptic"
            "ambientLight", "ambient_light" -> "ambient_light"
            "motionState", "motion_state" -> "motion_state"
            "ambientNoise", "ambient_noise" -> "ambient_noise"
            "readClipboard", "read_clipboard", "writeClipboard", "write_clipboard" -> "read_write_clipboard"
            else -> null
        }
    }

    private suspend fun executeAction(action: ParsedAction): ActionResult {
        return try {
            when (action.functionName) {
                "sendSms", "send_sms" -> smsHandler.execute(action.parameters)
                "makeCall", "make_call" -> callHandler.execute(action.parameters)
                "setAlarm", "set_alarm", "setTimer", "set_timer" -> alarmHandler.execute(
                    action.functionName.replace("_", "").replaceFirstChar { it.lowercase() }.let {
                        if (it.contains("timer", ignoreCase = true)) "setTimer" else "setAlarm"
                    },
                    action.parameters
                )
                "setVolume", "set_volume", "set_stream_volume" -> systemSettingsHandler.executeSetStreamVolume(action.parameters)
                "toggleFlashlight", "toggle_flashlight" -> systemSettingsHandler.executeToggleFlashlight(action.parameters)
                "toggleDnd", "toggle_dnd" -> dndHandler.execute(action.parameters)
                "openApp", "open_app" -> appLaunchHandler.execute(action.parameters)
                "navigate" -> navigationHandler.execute(action.parameters)
                "mediaControl", "media_control" -> mediaHandler.execute(action.parameters)
                "setBrightness", "set_brightness" -> brightnessHandler.execute(action.parameters)
                "createCalendarEvent", "create_calendar_event" -> calendarHandler.execute(action.parameters)
                "searchInternet", "search_internet" -> searchHandler.execute(action.parameters)
                "batteryDiagnostics", "battery_diagnostics" -> telemetryHandler.executeBatteryDiagnostics()
                "networkInfo", "network_info" -> telemetryHandler.executeNetworkInfo()
                "storageMemory", "storage_memory" -> telemetryHandler.executeStorageMemory()
                "playHaptic", "play_haptic" -> audioHapticsHandler.executePlayHaptic(action.parameters)
                "ambientLight", "ambient_light" -> ambientLightHandler.execute()
                "motionState", "motion_state" -> motionStateHandler.execute()
                "ambientNoise", "ambient_noise" -> ambientNoiseHandler.execute()
                "readClipboard", "read_clipboard" -> clipboardHandler.executeRead()
                "writeClipboard", "write_clipboard" -> clipboardHandler.executeWrite(action.parameters)
                else -> ActionResult.Error("Unknown action: " + action.functionName)
            }
        } catch (e: Exception) {
            ActionResult.Error("Action '" + action.functionName + "' failed: " + e.message, e)
        }
    }
}
