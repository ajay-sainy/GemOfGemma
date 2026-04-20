package com.gemofgemma.core.model

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val requiredPermission: String? = null,
    val isDangerousPermission: Boolean = false
) {
    companion object {
        val ALL_TOOLS = listOf(
            // Hardware & Environment
            ToolDefinition(
                id = "ambient_light",
                name = "Ambient Light Level",
                description = "Silently access the light sensor to determine if you are in a dark room.",
                category = ToolCategory.HARDWARE_TELEMETRY
            ),
            ToolDefinition(
                id = "motion_state",
                name = "Motion State",
                description = "Silently evaluate device motion (walking, running, stationary).",
                category = ToolCategory.HARDWARE_TELEMETRY,
                requiredPermission = "android.permission.ACTIVITY_RECOGNITION",
                isDangerousPermission = true
            ),
            ToolDefinition(
                id = "battery_diagnostics",
                name = "Battery Diagnostics",
                description = "Check exact battery percentage, temperature, and charging state.",
                category = ToolCategory.HARDWARE_TELEMETRY
            ),
            ToolDefinition(
                id = "storage_memory",
                name = "Device Storage & Memory",
                description = "Silently evaluate available RAM and free disk space.",
                category = ToolCategory.HARDWARE_TELEMETRY
            ),

            // Network & Connectivity
            ToolDefinition(
                id = "network_info",
                name = "Network Info",
                description = "Read WiFi conditions and cellular connection status.",
                category = ToolCategory.NETWORK_CONNECTIVITY,
                requiredPermission = "android.permission.ACCESS_NETWORK_STATE"
            ),

            // Background Audio & Haptics
            ToolDefinition(
                id = "ambient_noise",
                name = "Measure Ambient Noise",
                description = "Silently sample the microphone for 1-2s to compute background decibel levels.",
                category = ToolCategory.BACKGROUND_AUDIO_HAPTICS,
                requiredPermission = "android.permission.RECORD_AUDIO",
                isDangerousPermission = true
            ),
            ToolDefinition(
                id = "play_haptic",
                name = "Play Haptic Pattern",
                description = "Dispatch custom vibration patterns like Morse code.",
                category = ToolCategory.BACKGROUND_AUDIO_HAPTICS,
                requiredPermission = "android.permission.VIBRATE"
            ),
            
            // Data & Compute Utilities
            ToolDefinition(
                id = "read_write_clipboard",
                name = "Clipboard Access",
                description = "Move data in and out of the clipboard silently.",
                category = ToolCategory.DATA_COMPUTE
            ),

            // System Settings
            ToolDefinition(
                id = "set_stream_volume",
                name = "Adjust Media Volume",
                description = "Modify media, alarm, or ringer volume selectively.",
                category = ToolCategory.SYSTEM_SETTINGS
            ),
            ToolDefinition(
                id = "toggle_flashlight",
                name = "Toggle Flashlight",
                description = "Turn the camera torch on/off silently.",
                category = ToolCategory.SYSTEM_SETTINGS,
                requiredPermission = "android.permission.CAMERA",
                isDangerousPermission = true
            ),

            // Phone Actions
            ToolDefinition(
                id = "sendSms",
                name = "Send SMS",
                description = "Send an SMS text message to a contact.",
                category = ToolCategory.PHONE_ACTIONS,
                requiredPermission = "android.permission.SEND_SMS",
                isDangerousPermission = true
            ),
            ToolDefinition(
                id = "makeCall",
                name = "Make Call",
                description = "Make a phone call to a contact.",
                category = ToolCategory.PHONE_ACTIONS,
                requiredPermission = "android.permission.CALL_PHONE",
                isDangerousPermission = true
            ),
            ToolDefinition(
                id = "setAlarm",
                name = "Set Alarm",
                description = "Set an alarm for a specific time.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "setTimer",
                name = "Set Timer",
                description = "Set a countdown timer.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "toggleFlashlight",
                name = "Toggle Flashlight (Voice)",
                description = "Toggle the flashlight on or off via voice command.",
                category = ToolCategory.PHONE_ACTIONS,
                requiredPermission = "android.permission.CAMERA",
                isDangerousPermission = true
            ),
            ToolDefinition(
                id = "setVolume",
                name = "Set Volume",
                description = "Set media volume to a percentage 0-100.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "toggleDnd",
                name = "Toggle Do Not Disturb",
                description = "Toggle Do Not Disturb mode.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "openApp",
                name = "Open App",
                description = "Open an app by name.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "navigate",
                name = "Navigate",
                description = "Navigate to an address or place.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "mediaControl",
                name = "Media Control",
                description = "Control media playback: play, pause, next, previous, stop.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "setBrightness",
                name = "Set Brightness",
                description = "Set screen brightness to a percentage 0-100.",
                category = ToolCategory.PHONE_ACTIONS
            ),
            ToolDefinition(
                id = "createCalendarEvent",
                name = "Create Calendar Event",
                description = "Create a calendar event.",
                category = ToolCategory.PHONE_ACTIONS,
                requiredPermission = "android.permission.WRITE_CALENDAR",
                isDangerousPermission = true
            )
        )

        val PHONE_ACTION_IDS: Set<String> = ALL_TOOLS
            .filter { it.category == ToolCategory.PHONE_ACTIONS }
            .map { it.id }
            .toSet()
    }
}
