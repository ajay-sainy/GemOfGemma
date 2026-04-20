package com.gemofgemma.ai.tools

import com.gemofgemma.actions.ActionDispatcher
import com.gemofgemma.actions.model.ParsedAction
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

/**
 * Native LiteRT-LM ToolSet defining all phone actions and diagnostic tools
 * that Gemma 4 can invoke via native tool-calling tokens.
 *
 * Methods are annotated with [@Tool]/[@ToolParam] so LiteRT-LM auto-generates
 * tool schemas for the model. With `automaticToolCalling = false` (current
 * config), these methods are NOT auto-invoked; tool calls are handled
 * manually in [com.gemofgemma.ai.GemmaService]. The implementations here
 * serve as a fallback if automatic calling is ever enabled.
 */
class GemmaToolSet(
    private val actionDispatcher: ActionDispatcher
) : ToolSet {

    // ── Phone Actions ──────────────────────────────────────────────────

    @Tool(description = "Send an SMS text message to a contact")
    fun sendSms(
        @ToolParam(description = "Phone number to send to") phoneNumber: String,
        @ToolParam(description = "Message text to send") message: String
    ): Map<String, Any> = dispatch("sendSms", mapOf("phoneNumber" to phoneNumber, "message" to message))

    @Tool(description = "Make a phone call to a contact")
    fun makeCall(
        @ToolParam(description = "Phone number to call") phoneNumber: String
    ): Map<String, Any> = dispatch("makeCall", mapOf("phoneNumber" to phoneNumber))

    @Tool(description = "Set an alarm for a specific time")
    fun setAlarm(
        @ToolParam(description = "Hour in 24-hour format (0-23)") hour: Int,
        @ToolParam(description = "Minutes (0-59)") minutes: Int,
        @ToolParam(description = "Optional label for the alarm") label: String
    ): Map<String, Any> = dispatch("setAlarm", buildMap {
        put("hour", hour)
        put("minutes", minutes)
        if (label.isNotEmpty()) put("label", label)
    })

    @Tool(description = "Set a countdown timer")
    fun setTimer(
        @ToolParam(description = "Timer duration in seconds") durationSeconds: Int,
        @ToolParam(description = "Optional label for the timer") label: String
    ): Map<String, Any> = dispatch("setTimer", buildMap {
        put("durationSeconds", durationSeconds)
        if (label.isNotEmpty()) put("label", label)
    })

    @Tool(description = "Toggle the flashlight on or off")
    fun toggleFlashlight(
        @ToolParam(description = "true to turn on, false to turn off") on: Boolean
    ): Map<String, Any> = dispatch("toggleFlashlight", mapOf("on" to on))

    @Tool(description = "Set media volume to a percentage 0-100")
    fun setVolume(
        @ToolParam(description = "Volume percentage (0-100)") percent: Int
    ): Map<String, Any> = dispatch("setVolume", mapOf("percent" to percent))

    @Tool(description = "Toggle Do Not Disturb mode")
    fun toggleDnd(
        @ToolParam(description = "true to enable DND, false to disable") enabled: Boolean
    ): Map<String, Any> = dispatch("toggleDnd", mapOf("enabled" to enabled))

    @Tool(description = "Open an app by name")
    fun openApp(
        @ToolParam(description = "Name of the app to open") appName: String
    ): Map<String, Any> = dispatch("openApp", mapOf("appName" to appName))

    @Tool(description = "Navigate to an address or place")
    fun navigate(
        @ToolParam(description = "Address or place name") destination: String
    ): Map<String, Any> = dispatch("navigate", mapOf("destination" to destination))

    @Tool(description = "Control media playback: play, pause, next, previous, stop")
    fun mediaControl(
        @ToolParam(description = "One of: play, pause, next, previous, stop") action: String
    ): Map<String, Any> = dispatch("mediaControl", mapOf("action" to action))

    @Tool(description = "Set screen brightness to a percentage 0-100")
    fun setBrightness(
        @ToolParam(description = "Brightness percentage (0-100)") percent: Int
    ): Map<String, Any> = dispatch("setBrightness", mapOf("percent" to percent))

    @Tool(description = "Create a calendar event")
    fun createCalendarEvent(
        @ToolParam(description = "Event title") title: String,
        @ToolParam(description = "Start time in yyyy-MM-dd'T'HH:mm format") startTime: String,
        @ToolParam(description = "End time in yyyy-MM-dd'T'HH:mm format") endTime: String,
        @ToolParam(description = "Event location") location: String
    ): Map<String, Any> = dispatch("createCalendarEvent", buildMap {
        put("title", title)
        put("startTime", startTime)
        put("endTime", endTime)
        if (location.isNotEmpty()) put("location", location)
    })

    // ── Diagnostic / Sensor Tools ──────────────────────────────────────

    @Tool(description = "Check battery percentage, temperature, and charging state")
    fun batteryDiagnostics(): Map<String, Any> = dispatch("battery_diagnostics", emptyMap())

    @Tool(description = "Read WiFi conditions and cellular connection status")
    fun networkInfo(): Map<String, Any> = dispatch("network_info", emptyMap())

    @Tool(description = "Evaluate available RAM and free disk space")
    fun storageMemory(): Map<String, Any> = dispatch("storage_memory", emptyMap())

    @Tool(description = "Play a haptic vibration pattern")
    fun playHaptic(
        @ToolParam(description = "Pattern: 1=short, 2=long") pattern: Int
    ): Map<String, Any> = dispatch("play_haptic", mapOf("pattern" to pattern))

    @Tool(description = "Read ambient light level from light sensor")
    fun ambientLight(): Map<String, Any> = dispatch("ambient_light", emptyMap())

    @Tool(description = "Evaluate device motion state (walking, running, stationary)")
    fun motionState(): Map<String, Any> = dispatch("motion_state", emptyMap())

    @Tool(description = "Sample microphone to measure background noise in decibels")
    fun ambientNoise(): Map<String, Any> = dispatch("ambient_noise", emptyMap())

    @Tool(description = "Read the current clipboard contents")
    fun readClipboard(): Map<String, Any> = dispatch("read_clipboard", emptyMap())

    @Tool(description = "Write text to the clipboard")
    fun writeClipboard(
        @ToolParam(description = "Text to copy to clipboard") text: String
    ): Map<String, Any> = dispatch("write_clipboard", mapOf("text" to text))

    // ── Dispatch helper ────────────────────────────────────────────────

    private fun dispatch(functionName: String, params: Map<String, Any>): Map<String, Any> {
        val result = runBlocking {
            actionDispatcher.dispatch(ParsedAction(functionName, params))
        }
        return mapOf("status" to result.toString())
    }
}
