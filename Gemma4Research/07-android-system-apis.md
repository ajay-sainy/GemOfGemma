# Android System APIs & Intents for On-Device AI (Gemma 4) Tool-Calling

> **Research Date:** April 17, 2026  
> **Purpose:** Catalog all Android APIs/Intents that Gemma 4 on-device AI can trigger via tool-calling for our object detection + AI assistant app.  
> **Sources:** Official Android Developer Documentation (developer.android.com)

---

## Table of Contents
1. [Architecture: AI Tool-Calling + Android Intents](#1-architecture)
2. [Messaging & Communication](#2-messaging--communication)
3. [Alarms & Reminders](#3-alarms--reminders)
4. [Connectivity Toggles](#4-connectivity-toggles)
5. [Device Settings](#5-device-settings)
6. [Media & Camera](#6-media--camera)
7. [Apps & Navigation](#7-apps--navigation)
8. [Files & Storage](#8-files--storage)
9. [Accessibility & System](#9-accessibility--system)
10. [Gemma 4 Tool-Calling Integration Pattern](#10-gemma-4-tool-calling-integration-pattern)
11. [Key Restrictions & Limitations](#11-key-restrictions--limitations)

---

## 1. Architecture

The pattern for on-device AI + Android intents:

```
User Voice/Text → Gemma 4 On-Device Model → Function Call (tool) → Kotlin Handler → Android Intent/API → System Action
```

Gemma 4 supports **tool-calling / function-calling**: the model outputs a structured function call (name + arguments) which the app intercepts, validates, and executes as an Android Intent or API call.

---

## 2. Messaging & Communication

### 2.1 Send SMS

| Property | Value |
|---|---|
| **API** | `SmsManager.sendTextMessage()` |
| **Intent Alternative** | `Intent.ACTION_SENDTO` with `smsto:` URI |
| **Permission** | `SEND_SMS` (runtime) |
| **Min API** | 4 (SmsManager), 1 (Intent) |
| **User Confirmation** | SmsManager: silent if default SMS app; Intent: shows compose UI |

```kotlin
// Via Intent (user confirms) - RECOMMENDED for AI assistant
fun sendSmsIntent(context: Context, number: String, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:$number")
        putExtra("sms_body", message)
    }
    context.startActivity(intent)
}

// Via SmsManager (silent, requires SEND_SMS + default SMS app)
fun sendSmsSilent(context: Context, number: String, message: String) {
    val smsManager = context.getSystemService(SmsManager::class.java)
    smsManager.sendTextMessage(number, null, message, null, null)
}
```

**Limitation:** Apps targeting API 19+ that are NOT the default SMS app will have messages auto-written to SMS Provider by the system.

### 2.2 Send WhatsApp Message

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_SENDTO` with WhatsApp package |
| **Permission** | None |
| **Min API** | 1 |
| **User Confirmation** | Yes (opens WhatsApp compose) |

```kotlin
fun sendWhatsApp(context: Context, phone: String, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:$phone")
        putExtra("sms_body", message)
        setPackage("com.whatsapp")
    }
    context.startActivity(intent)
}

// Alternative: direct chat via URL
fun openWhatsAppChat(context: Context, phone: String, message: String) {
    val url = "https://wa.me/$phone?text=${Uri.encode(message)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
```

### 2.3 Make Phone Call

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_CALL` (direct) / `Intent.ACTION_DIAL` (dialer UI) |
| **Permission** | `CALL_PHONE` for ACTION_CALL; none for ACTION_DIAL |
| **Min API** | 1 |
| **User Confirmation** | ACTION_DIAL: yes; ACTION_CALL: no (but needs permission) |

```kotlin
// Opens dialer (no permission needed) - RECOMMENDED
fun dialNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$number")
    }
    context.startActivity(intent)
}

// Direct call (requires CALL_PHONE permission)
fun callNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_CALL).apply {
        data = Uri.parse("tel:$number")
    }
    context.startActivity(intent)
}
```

**Limitation:** ACTION_CALL cannot be used for emergency numbers. ACTION_DIAL should be used instead.

### 2.4 Send Email

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_SENDTO` with `mailto:` URI |
| **Permission** | None |
| **Min API** | 1 |
| **User Confirmation** | Yes (opens email compose) |

```kotlin
fun sendEmail(context: Context, to: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(intent)
}
```

### 2.5 Share Content

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_SEND` / `Intent.ACTION_SEND_MULTIPLE` |
| **Permission** | None |
| **Min API** | 1 |
| **User Confirmation** | Yes (chooser UI) |

```kotlin
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
```

---

## 3. Alarms & Reminders

### 3.1 Set Alarm

| Property | Value |
|---|---|
| **API** | `AlarmClock.ACTION_SET_ALARM` |
| **Permission** | `com.android.alarm.permission.SET_ALARM` |
| **Min API** | 9 |
| **User Confirmation** | Optional (use `EXTRA_SKIP_UI = true` to skip) |

```kotlin
fun setAlarm(context: Context, hour: Int, minutes: Int, message: String) {
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour)
        putExtra(AlarmClock.EXTRA_MINUTES, minutes)
        putExtra(AlarmClock.EXTRA_MESSAGE, message)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    }
    context.startActivity(intent)
}
```

### 3.2 Set Timer

| Property | Value |
|---|---|
| **API** | `AlarmClock.ACTION_SET_TIMER` |
| **Permission** | `com.android.alarm.permission.SET_ALARM` |
| **Min API** | 19 |
| **User Confirmation** | Optional (use `EXTRA_SKIP_UI = true`) |

```kotlin
fun setTimer(context: Context, seconds: Int, message: String) {
    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        putExtra(AlarmClock.EXTRA_MESSAGE, message)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    }
    context.startActivity(intent)
}
```

### 3.3 Create Calendar Event

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_INSERT` with `CalendarContract.Events.CONTENT_URI` |
| **Permission** | None (intent-based); `WRITE_CALENDAR` for direct API |
| **Min API** | 14 |
| **User Confirmation** | Yes (opens calendar editor) |

```kotlin
fun createCalendarEvent(context: Context, title: String, location: String, beginMs: Long, endMs: Long) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
    }
    context.startActivity(intent)
}
```

### 3.4 Create Reminder

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_CREATE_REMINDER` |
| **Permission** | None |
| **Min API** | 30 |
| **User Confirmation** | Yes |

```kotlin
fun createReminder(context: Context, title: String, text: String, timeMs: Long) {
    val intent = Intent(Intent.ACTION_CREATE_REMINDER).apply {
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TIME, timeMs)
    }
    context.startActivity(intent)
}
```

---

## 4. Connectivity Toggles

### 4.1 WiFi Settings

| Property | Value |
|---|---|
| **API** | `Settings.ACTION_WIFI_SETTINGS` / `Settings.Panel.ACTION_WIFI` |
| **Permission** | None (opens Settings UI) |
| **Min API** | 1 (Settings), 29 (Panel) |
| **User Confirmation** | Yes (user must toggle manually) |

```kotlin
// Open WiFi settings
fun openWifiSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
}

// Quick Settings panel (API 29+) - inline toggle
fun openWifiPanel(context: Context) {
    context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
}
```

**Limitation:** `WifiManager.setWifiEnabled()` deprecated in API 29+. Apps targeting Q+ cannot toggle WiFi programmatically. Only DO/PO and system apps are exempt.

### 4.2 Bluetooth Settings

| Property | Value |
|---|---|
| **API** | `Settings.ACTION_BLUETOOTH_SETTINGS` / `BluetoothAdapter.ACTION_REQUEST_ENABLE` |
| **Permission** | `BLUETOOTH_CONNECT` (API 31+) |
| **Min API** | 1 (Settings), 5 (enable request) |
| **User Confirmation** | Yes |

```kotlin
fun enableBluetooth(context: Context) {
    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    context.startActivity(intent)
}
```

**Limitation:** `BluetoothAdapter.enable()/disable()` deprecated in API 33+. Cannot toggle programmatically.

### 4.3 Airplane Mode

| Property | Value |
|---|---|
| **API** | `Settings.ACTION_AIRPLANE_MODE_SETTINGS` |
| **Permission** | None (opens Settings) |
| **Min API** | 3 |
| **User Confirmation** | Yes (user toggles) |

```kotlin
fun openAirplaneSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
}
```

**Limitation:** Cannot toggle airplane mode programmatically since API 17 for non-system apps.

### 4.4 Mobile Data / NFC / Hotspot

| Property | Value |
|---|---|
| **API** | `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` (API 29+), `Settings.ACTION_NFC_SETTINGS` (API 16+) |
| **Permission** | None (opens Settings UI) |
| **User Confirmation** | Yes |

```kotlin
fun openInternetPanel(context: Context) {
    context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
}

fun openNfcSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
}
```

---

## 5. Device Settings

### 5.1 Volume Control

| Property | Value |
|---|---|
| **API** | `AudioManager.adjustStreamVolume()` / `AudioManager.setStreamVolume()` |
| **Permission** | None |
| **Min API** | 1 |
| **User Confirmation** | No (runs silently; UI toast optional via `FLAG_SHOW_UI`) |

```kotlin
fun setMediaVolume(context: Context, volumePercent: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val targetVol = (maxVol * volumePercent / 100).coerceIn(0, maxVol)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
}

fun volumeUp(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
}
```

### 5.2 Brightness

| Property | Value |
|---|---|
| **API** | `Settings.System.SCREEN_BRIGHTNESS` / `Settings.ACTION_DISPLAY_SETTINGS` |
| **Permission** | `WRITE_SETTINGS` |
| **Min API** | 1 |
| **User Confirmation** | Requires user to grant WRITE_SETTINGS via `Settings.ACTION_MANAGE_WRITE_SETTINGS` |

```kotlin
fun setBrightness(context: Context, brightnessPercent: Int) {
    if (Settings.System.canWrite(context)) {
        val brightnessValue = (brightnessPercent * 255 / 100).coerceIn(0, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
    }
}
```

### 5.3 Do Not Disturb

| Property | Value |
|---|---|
| **API** | `NotificationManager.setInterruptionFilter()` |
| **Permission** | Notification Policy Access (via `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) |
| **Min API** | 23 |
| **User Confirmation** | Must grant notification policy access first |

```kotlin
fun enableDnd(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.isNotificationPolicyAccessGranted) {
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }
}
```

### 5.4 Flashlight / Torch

| Property | Value |
|---|---|
| **API** | `CameraManager.setTorchMode()` |
| **Permission** | None |
| **Min API** | 23 |
| **User Confirmation** | No (runs silently) |

```kotlin
fun toggleFlashlight(context: Context, on: Boolean) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList[0]
    cameraManager.setTorchMode(cameraId, on)
}
```

### 5.5 Screen Rotation / Location Services

| Property | Value |
|---|---|
| **Rotation** | `Settings.ACTION_AUTO_ROTATE_SETTINGS` (API 31+) |
| **Location** | `Settings.ACTION_LOCATION_SOURCE_SETTINGS` |
| **User Confirmation** | Yes |

```kotlin
fun openLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}
```

---

## 6. Media & Camera

### 6.1 Take a Photo

| Property | Value |
|---|---|
| **API** | `MediaStore.ACTION_IMAGE_CAPTURE` |
| **Permission** | `CAMERA` (if declared in manifest and targeting M+) |
| **Min API** | 3 |
| **User Confirmation** | Yes (opens camera app) |

```kotlin
fun takePhoto(context: Context, outputUri: Uri) {
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
    }
    context.startActivity(intent)
}
```

### 6.2 Record Video

| Property | Value |
|---|---|
| **API** | `MediaStore.ACTION_VIDEO_CAPTURE` |
| **Permission** | `CAMERA` (if declared) |
| **Min API** | 3 |
| **User Confirmation** | Yes |

```kotlin
fun recordVideo(context: Context) {
    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1) // high quality
    }
    context.startActivity(intent)
}
```

### 6.3 Play Music / Media Search

| Property | Value |
|---|---|
| **API** | `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` / `Intent.ACTION_VIEW` |
| **Permission** | None |
| **Min API** | 9 (search), 1 (view) |
| **User Confirmation** | Yes (opens media app) |

```kotlin
fun playMusicSearch(context: Context, query: String) {
    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
    }
    context.startActivity(intent)
}
```

### 6.4 Media Controls (Play/Pause/Skip)

| Property | Value |
|---|---|
| **API** | `AudioManager.dispatchMediaKeyEvent()` |
| **Permission** | None |
| **Min API** | 19 |
| **User Confirmation** | No (silent) |

```kotlin
fun mediaPlayPause(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
}

fun mediaNext(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
}
```

---

## 7. Apps & Navigation

### 7.1 Open an App

| Property | Value |
|---|---|
| **API** | `PackageManager.getLaunchIntentForPackage()` |
| **Permission** | None (but QUERY_ALL_PACKAGES may be needed for API 30+) |
| **Min API** | 1 |
| **User Confirmation** | No |

```kotlin
fun openApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    }
}
```

### 7.2 Open URL in Browser

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_VIEW` with `http`/`https` URI |
| **Permission** | None |
| **Min API** | 1 |
| **User Confirmation** | No (or chooser if multiple browsers) |

```kotlin
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
```

### 7.3 Navigate to Location (Maps)

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_VIEW` with `geo:` URI |
| **Permission** | None |
| **Min API** | 1 |
| **User Confirmation** | No (or chooser) |

```kotlin
fun navigateTo(context: Context, address: String) {
    val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
    context.startActivity(intent)
}

fun navigateWithDirections(context: Context, destLat: Double, destLng: Double) {
    val uri = Uri.parse("google.navigation:q=$destLat,$destLng")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    context.startActivity(intent)
}
```

### 7.4 Open Settings Pages

| Property | Value |
|---|---|
| **API** | `Settings.ACTION_SETTINGS` and many sub-pages |
| **Permission** | None |
| **Min API** | 1+ |
| **User Confirmation** | Yes (opens the settings page) |

```kotlin
fun openSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_SETTINGS))
}

fun openSoundSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
}

fun openDisplaySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
}
```

---

## 8. Files & Storage

### 8.1 Open a File

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_VIEW` / `Intent.ACTION_OPEN_DOCUMENT` |
| **Permission** | None (SAF-based) |
| **Min API** | 19 (ACTION_OPEN_DOCUMENT) |
| **User Confirmation** | Yes |

```kotlin
fun openFile(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
```

### 8.2 Create a Note

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_CREATE_NOTE` |
| **Permission** | None |
| **Min API** | 34 |
| **User Confirmation** | Yes |

```kotlin
fun createNote(context: Context, useStylus: Boolean = false) {
    val intent = Intent(Intent.ACTION_CREATE_NOTE).apply {
        putExtra(Intent.EXTRA_USE_STYLUS_MODE, useStylus)
    }
    context.startActivity(intent)
}
```

### 8.3 Access Contacts

| Property | Value |
|---|---|
| **API** | `Intent.ACTION_PICK` with `ContactsContract.Contacts.CONTENT_TYPE` |
| **Permission** | None for picker; `READ_CONTACTS` for direct query |
| **Min API** | 1 |
| **User Confirmation** | Yes (picker UI) |

```kotlin
fun pickContact(context: Context) {
    val intent = Intent(Intent.ACTION_PICK).apply {
        type = ContactsContract.Contacts.CONTENT_TYPE
    }
    context.startActivity(intent)
}
```

---

## 9. Accessibility & System

### 9.1 Read Notifications

| Property | Value |
|---|---|
| **API** | `NotificationListenerService` |
| **Permission** | Notification Listener Access (user grants via Settings) |
| **Min API** | 18 |
| **User Confirmation** | Must enable in Settings |

Requires implementing a `NotificationListenerService` and user enabling it in `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.

### 9.2 Text-to-Speech

| Property | Value |
|---|---|
| **API** | `android.speech.tts.TextToSpeech` |
| **Permission** | None |
| **Min API** | 4 |
| **User Confirmation** | No (runs silently) |

```kotlin
class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }
    
    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance-1")
    }
}
```

### 9.3 Clipboard Access

| Property | Value |
|---|---|
| **API** | `ClipboardManager` |
| **Permission** | None (but read restrictions in API 33+) |
| **Min API** | 11 |
| **User Confirmation** | Toast shown to user on API 33+ when reading |

```kotlin
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", text))
}
```

**Limitation:** Starting Android 13 (API 33), apps can only read clipboard data when the app is in the foreground. A toast notification is shown to the user.

---

## 10. Gemma 4 Tool-Calling Integration Pattern

### Architecture for On-Device AI Assistant

```kotlin
// 1. Define tool schemas for Gemma 4
val tools = listOf(
    Tool(name = "send_sms", description = "Send a text message",
         params = mapOf("number" to "string", "message" to "string")),
    Tool(name = "set_alarm", description = "Set an alarm",
         params = mapOf("hour" to "int", "minutes" to "int", "label" to "string")),
    Tool(name = "set_volume", description = "Set media volume",
         params = mapOf("percent" to "int")),
    Tool(name = "toggle_flashlight", description = "Turn flashlight on or off",
         params = mapOf("on" to "boolean")),
    Tool(name = "open_url", description = "Open a URL in browser",
         params = mapOf("url" to "string")),
    Tool(name = "navigate_to", description = "Navigate to an address",
         params = mapOf("address" to "string")),
    Tool(name = "take_photo", description = "Open camera to take a photo",
         params = mapOf()),
    Tool(name = "play_music", description = "Play music by search query",
         params = mapOf("query" to "string")),
    // ... more tools
)

// 2. Process Gemma 4 model output
fun handleToolCall(context: Context, toolCall: ToolCall) {
    when (toolCall.name) {
        "send_sms" -> sendSmsIntent(context, toolCall.args["number"]!!, toolCall.args["message"]!!)
        "set_alarm" -> setAlarm(context, toolCall.args["hour"]!!.toInt(), toolCall.args["minutes"]!!.toInt(), toolCall.args["label"] ?: "")
        "set_volume" -> setMediaVolume(context, toolCall.args["percent"]!!.toInt())
        "toggle_flashlight" -> toggleFlashlight(context, toolCall.args["on"]!!.toBoolean())
        "open_url" -> openUrl(context, toolCall.args["url"]!!)
        "navigate_to" -> navigateTo(context, toolCall.args["address"]!!)
        "take_photo" -> takePhoto(context, outputUri)
        "play_music" -> playMusicSearch(context, toolCall.args["query"]!!)
    }
}
```

### Key Design Principles

1. **Prefer Intent-based actions** over direct API calls — they show user confirmation and work without special permissions
2. **Validate all tool call arguments** before executing — sanitize phone numbers, URLs, etc.
3. **Check `resolveActivity()` before `startActivity()`** to avoid crashes
4. **Handle Android 10+ background activity start restrictions** — the AI must trigger actions only when app is in foreground
5. **Request only needed permissions** at runtime using the Android permission model

---

## 11. Key Restrictions & Limitations

| Restriction | Details |
|---|---|
| **Background Activity Starts** | Android 10+ restricts starting activities from the background. AI-triggered intents must originate from a foreground app/service. |
| **WiFi/BT Toggle** | `WifiManager.setWifiEnabled()` deprecated API 29+. `BluetoothAdapter.enable()/disable()` deprecated API 33+. Use Settings panels instead. |
| **SMS Sending** | Silent sending requires being the default SMS app or holding `SEND_SMS` with appropriate role. |
| **Package Visibility** | Android 11+ (API 30) requires `<queries>` in manifest to see other apps. |
| **Notification Policy** | Modifying DND requires user-granted Notification Policy Access. |
| **WRITE_SETTINGS** | Requires explicit user grant via `Settings.ACTION_MANAGE_WRITE_SETTINGS`. |
| **Clipboard Read** | Android 13+ shows toast when clipboard is read; content only accessible in foreground. |
| **Camera Permission** | If declared in manifest but not granted, camera intents throw SecurityException on API 23+. |

### Summary of What Can Run Silently vs. Needs User Confirmation

| Silent (No UI) | User Confirmation Required |
|---|---|
| Volume control | Send SMS (via Intent) |
| Flashlight toggle | Phone call (via Intent) |
| Media controls (play/pause/skip) | Set alarm (unless SKIP_UI) |
| TTS speech output | Calendar events |
| Clipboard write | Camera capture |
| DND (if policy access granted) | Map navigation |
| Brightness (if WRITE_SETTINGS) | Open apps/URLs |
| | WiFi/BT/Airplane toggles |
