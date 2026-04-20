package com.gemofgemma.ai.tools

/**
 * Structured tool/function definitions for all phone actions that Gemma 4
 * can invoke via function-calling.
 *
 * These definitions serve two purposes:
 * 1. Generate the tool description text embedded in voice command system prompts
 * 2. Provide a structured schema for future use with LiteRT-LM's native ToolSet API
 *
 * Each tool maps to an ActionHandler in the :actions module.
 */
object PhoneActionToolSet {

    data class ToolDefinition(
        val name: String,
        val description: String,
        val parameters: List<ToolParameter>
    )

    data class ToolParameter(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = true
    )

    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "sendSms",
            description = "Send an SMS text message to a contact",
            parameters = listOf(
                ToolParameter("phoneNumber", "String", "Phone number to send to"),
                ToolParameter("message", "String", "Message text to send")
            )
        ),
        ToolDefinition(
            name = "makeCall",
            description = "Make a phone call to a contact",
            parameters = listOf(
                ToolParameter("phoneNumber", "String", "Phone number to call")
            )
        ),
        ToolDefinition(
            name = "setAlarm",
            description = "Set an alarm for a specific time",
            parameters = listOf(
                ToolParameter("hour", "Int", "Hour in 24-hour format (0-23)"),
                ToolParameter("minutes", "Int", "Minutes (0-59)"),
                ToolParameter("label", "String", "Label for the alarm", required = false)
            )
        ),
        ToolDefinition(
            name = "setTimer",
            description = "Set a countdown timer",
            parameters = listOf(
                ToolParameter("durationSeconds", "Int", "Timer duration in seconds"),
                ToolParameter("label", "String", "Label for the timer", required = false)
            )
        ),
        ToolDefinition(
            name = "toggleFlashlight",
            description = "Toggle the flashlight on or off",
            parameters = listOf(
                ToolParameter("on", "Boolean", "true to turn on, false to turn off")
            )
        ),
        ToolDefinition(
            name = "setVolume",
            description = "Set media volume to a percentage 0-100",
            parameters = listOf(
                ToolParameter("percent", "Int", "Volume percentage (0-100)")
            )
        ),
        ToolDefinition(
            name = "toggleDnd",
            description = "Toggle Do Not Disturb mode",
            parameters = listOf(
                ToolParameter("enabled", "Boolean", "true to enable DND, false to disable")
            )
        ),
        ToolDefinition(
            name = "openApp",
            description = "Open an app by name",
            parameters = listOf(
                ToolParameter("appName", "String", "Name of the app to open")
            )
        ),
        ToolDefinition(
            name = "navigate",
            description = "Navigate to an address or place",
            parameters = listOf(
                ToolParameter("destination", "String", "Address or place name")
            )
        ),
        ToolDefinition(
            name = "mediaControl",
            description = "Control media playback: play, pause, next, previous, stop",
            parameters = listOf(
                ToolParameter("action", "String", "One of: play, pause, next, previous, stop")
            )
        ),
        ToolDefinition(
            name = "setBrightness",
            description = "Set screen brightness to a percentage 0-100",
            parameters = listOf(
                ToolParameter("percent", "Int", "Brightness percentage (0-100)")
            )
        ),
        ToolDefinition(
            name = "createCalendarEvent",
            description = "Create a calendar event",
            parameters = listOf(
                ToolParameter("title", "String", "Event title"),
                ToolParameter("startTime", "String", "Start time in yyyy-MM-dd'T'HH:mm format"),
                ToolParameter("endTime", "String", "End time in yyyy-MM-dd'T'HH:mm format"),
                ToolParameter("location", "String", "Event location", required = false)
            )
        )
    )
}
