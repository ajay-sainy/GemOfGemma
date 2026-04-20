# Silent & Autonomous Execution on Android with Gemma 4

> **Research Date:** April 17, 2026
> **Author:** Elaine (ML Engineer)
> **Purpose:** Deep-dive into which Android APIs can operate fully silently/autonomously (no user confirmation needed) vs which absolutely require user interaction. Research workarounds, Accessibility Services, Device Admin, and Device Owner APIs.
> **Sources:** Official Android Developer Documentation, AccessibilityService API, DevicePolicyManager API, SpeechRecognizer API

---

## Table of Contents
1. [Fully Silent APIs](#1-fully-silent-apis)
2. [Accessibility Service Approach](#2-accessibility-service-approach)
3. [Device Admin / Device Owner APIs](#3-device-admin--device-owner-apis)
4. [Android Intent Flags and Tricks](#4-android-intent-flags-and-tricks)
5. [Android Version Restrictions](#5-android-version-restrictions)
6. [Command-Only Architecture Pattern](#6-command-only-architecture-pattern)
7. [Notification-Based Interaction](#7-notification-based-interaction)
8. [Recommended Architecture for Zero-Intervention AI Assistant](#8-recommended-architecture)

---

## 1. Fully Silent APIs

These APIs can execute **without any user interaction** once the required permission is granted at install time or first-run setup.

### 1.1 Tier 1: Zero Permission Silent Actions

These require NO runtime permissions and execute completely silently:

| Action | API | Notes |
|--------|-----|-------|
| **Set volume** | `AudioManager.setStreamVolume()` / `adjustStreamVolume()` | All stream types (media, ring, alarm, notification). No permission needed. |
| **Toggle flashlight** | `CameraManager.setTorchMode()` | Completely silent. API 23+. |
| **Media controls** | `AudioManager.dispatchMediaKeyEvent()` | Play/pause/skip/previous. Works on any active media session. API 19+. |
| **Text-to-Speech** | `TextToSpeech.speak()` | Speaks text audibly. No permission. API 4+. |
| **Write to clipboard** | `ClipboardManager.setPrimaryClip()` | Silent write. No permission. API 11+. |
| **Vibrate (short)** | `Vibrator.vibrate()` | Requires `VIBRATE` permission (normal, auto-granted). |
| **Post notification** | `NotificationManager.notify()` | Requires `POST_NOTIFICATIONS` (API 33+, runtime). Once granted, silent. |
| **Cancel notification** | `NotificationManager.cancel()` | Silent. No extra permission. |

### 1.2 Tier 2: One-Time Permission Grant, Then Silent

These require a one-time user grant during setup, then execute silently forever:

| Action | API | Permission | Grant Method |
|--------|-----|------------|--------------|
| **Send SMS silently** | `SmsManager.sendTextMessage()` | `SEND_SMS` (runtime) + default SMS app role | Runtime dialog + role request |
| **Make phone call** | `Intent.ACTION_CALL` | `CALL_PHONE` (runtime) | Runtime dialog |
| **Set alarm (skip UI)** | `AlarmClock.ACTION_SET_ALARM` + `EXTRA_SKIP_UI=true` | `SET_ALARM` (normal, auto-granted) | None needed |
| **Set timer (skip UI)** | `AlarmClock.ACTION_SET_TIMER` + `EXTRA_SKIP_UI=true` | `SET_ALARM` (normal, auto-granted) | None needed |
| **Create calendar event** | `CalendarContract` ContentProvider | `WRITE_CALENDAR` (runtime) | Runtime dialog |
| **Read contacts** | `ContactsContract` ContentProvider | `READ_CONTACTS` (runtime) | Runtime dialog |
| **Set brightness** | `Settings.System.putInt()` | `WRITE_SETTINGS` (special) | User grants via Settings UI |
| **Toggle DND** | `NotificationManager.setInterruptionFilter()` | Notification Policy Access (special) | User grants via Settings UI |
| **Read notifications** | `NotificationListenerService` | Notification Listener Access (special) | User enables in Settings |
| **Record audio** | `MediaRecorder` / `AudioRecord` | `RECORD_AUDIO` (runtime) | Runtime dialog |
| **Access location** | `LocationManager` / `FusedLocationProviderClient` | `ACCESS_FINE_LOCATION` (runtime) | Runtime dialog |
| **Read call log** | `CallLog.Calls` ContentProvider | `READ_CALL_LOG` (runtime) | Runtime dialog |
| **Read calendar** | `CalendarContract` ContentProvider | `READ_CALENDAR` (runtime) | Runtime dialog |
| **Camera capture (programmatic)** | `Camera2` API directly | `CAMERA` (runtime) | Runtime dialog |

### 1.3 Tier 3: Actions That ALWAYS Require User Interaction

These **cannot** be made silent through normal means:

| Action | Why Not Silent | Workaround |
|--------|---------------|------------|
| **Send WhatsApp/Telegram message** | Opens 3rd-party app compose UI | AccessibilityService can auto-tap send |
| **Send email** | Opens email app compose UI | AccessibilityService can auto-tap send |
| **Toggle WiFi** | `WifiManager.setWifiEnabled()` deprecated API 29+ | Device Owner API or AccessibilityService |
| **Toggle Bluetooth** | `BluetoothAdapter.enable()/disable()` deprecated API 33+ | Device Owner API or AccessibilityService |
| **Toggle Airplane Mode** | No programmatic API since API 17 for non-system apps | Device Owner or AccessibilityService |
| **Open camera for photo** | `MediaStore.ACTION_IMAGE_CAPTURE` opens camera app | Use Camera2 API directly instead |
| **Navigate (Maps)** | Opens Maps app | Can launch silently with FLAG_ACTIVITY_NEW_TASK but Maps UI is shown |
| **Share content** | Shows system chooser dialog | No workaround |
| **Install/uninstall apps** | Security-critical, requires user approval | Device Owner only |

### 1.4 Complete List of Actions Possible After First-Run Permission Setup

Once the user completes a one-time setup flow granting all permissions, the app can autonomously:

1. Send SMS messages silently (if default SMS app)
2. Make phone calls silently
3. Set alarms and timers without UI
4. Create/modify calendar events
5. Control all volume streams
6. Toggle flashlight
7. Control media playback (play/pause/skip)
8. Speak responses via TTS
9. Copy text to clipboard
10. Set screen brightness
11. Toggle Do Not Disturb
12. Read incoming notifications
13. Record audio (for voice commands)
14. Get device location
15. Read contacts for lookups
16. Read call log
17. Post notifications with results
18. Write files to app-specific storage
19. Schedule future tasks (AlarmManager/WorkManager)
20. Open apps by package name
21. Open URLs in browser
22. Take screenshots (via AccessibilityService)
23. Read screen content (via AccessibilityService)
24. Auto-tap UI elements in other apps (via AccessibilityService)

---

## 2. Accessibility Service Approach

### 2.1 Overview

`AccessibilityService` is the **most powerful tool** for autonomous execution on Android. It can:

- **Read any app's UI tree** — get all visible text, buttons, input fields
- **Perform clicks and gestures** on any UI element in any app
- **Dispatch touch gestures** to the screen (`dispatchGesture()`)
- **Perform global actions** — back, home, recents, notifications, lock screen, screenshot
- **Intercept key events** before they reach apps
- **Take screenshots** of any display or window (API 30+)
- **Draw overlays** on top of any window (API 34+)
- **Act as an input method** — type text into fields (API 33+)

### 2.2 What AccessibilityService CAN Do (Relevant to Our App)

```kotlin
class AiAssistantAccessibilityService : AccessibilityService() {

    // Auto-tap the "Send" button in WhatsApp
    fun autoSendWhatsApp(phoneNumber: String, message: String) {
        // 1. Launch WhatsApp chat via intent
        // 2. Wait for WhatsApp UI to load
        // 3. Find the message input field via AccessibilityNodeInfo
        // 4. Set text using ACTION_SET_TEXT
        // 5. Find the send button
        // 6. Perform ACTION_CLICK on it
    }

    // Find and click any UI element by text/description
    fun findAndClick(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    // Perform global actions silently
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun lockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // Dispatch custom touch gestures
    fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(
            path, 0, ViewConfiguration.getTapTimeout().toLong()
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // Set text in any focused input field (API 33+ with FLAG_INPUT_METHOD_EDITOR)
    fun typeText(text: String) {
        val inputMethod = getInputMethod()
        inputMethod?.getCurrentInputConnection()?.commitText(text, 1)
    }
}
```

### 2.3 AccessibilityService Configuration

```xml
<!-- res/xml/accessibility_service_config.xml -->
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagRequestTouchExplorationMode|flagInputMethodEditor"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100"
    android:settingsActivity=".settings.AccessibilitySettingsActivity"
    android:description="@string/accessibility_service_description" />
```

### 2.4 Permission Requirements

| Requirement | Details |
|-------------|---------|
| **User must enable manually** | Settings > Accessibility > [Your Service] > Toggle ON |
| **Cannot be enabled programmatically** | System enforces manual user action |
| **Manifest declaration** | Must declare `BIND_ACCESSIBILITY_SERVICE` permission |
| **Survives reboot** | Once enabled, stays enabled until user disables |
| **One-time setup** | User only needs to do this once |

### 2.5 Play Store Restrictions

Google Play has **strict policies** on AccessibilityService usage:

- **Must be an accessibility tool** — Google reviews and may reject apps using it for automation
- **Restricted API** — Must declare in Play Console why you use it
- **Can result in app removal** if used for non-accessibility purposes
- **Exemption path**: If the app is genuinely an AI assistant for accessibility (e.g., helping visually impaired users), it may be approved
- **Sideloading**: No restrictions if distributed outside Play Store (e.g., enterprise deployment, direct APK)

### 2.6 Real Apps Using AccessibilityService for Automation

| App | Use Case | Distribution |
|-----|----------|-------------|
| **Tasker** | Full device automation — taps UI, reads screens, fills forms | Play Store (grandfathered) |
| **MacroDroid** | Automated macro execution including UI interactions | Play Store |
| **Auto Clicker** | Automated tapping at specified coordinates | Play Store |
| **IFTTT** | Limited automation via accessibility | Play Store |
| **Samsung Bixby Routines** | System-level automation (OEM privilege) | Pre-installed |
| **Google Assistant** | Voice-driven automation, screen reading | Pre-installed (system app) |
| **LastPass / Bitwarden** | Auto-fill passwords in any app | Play Store (accessibility use) |

### 2.7 Limitations of AccessibilityService

- **Cannot toggle WiFi/BT/Airplane directly** — but CAN navigate to Settings and tap the toggle
- **Cannot bypass secure windows** (`FLAG_SECURE`) — screenshots will fail
- **Latency** — UI tree traversal and click dispatching adds 100-500ms per action
- **Fragile** — UI element IDs/text change between app versions
- **Cannot interact with system dialogs** in some cases (permission dialogs)
- **Performance overhead** — continuous event monitoring uses battery

---

## 3. Device Admin / Device Owner APIs

### 3.1 Device Admin (DeviceAdminReceiver)

**What it provides:**

| Capability | API | Silent? |
|-----------|-----|---------|
| Lock device immediately | `DevicePolicyManager.lockNow()` | Yes |
| Wipe device data | `DevicePolicyManager.wipeData()` | Yes |
| Set password requirements | `setPasswordQuality()`, `setPasswordMinimumLength()` | Yes |
| Disable camera | `setCameraDisabled()` | Yes |
| Set max inactivity lock timeout | `setMaximumTimeToLock()` | Yes |
| Require storage encryption | `setStorageEncryption()` | Yes |

**Setup requirement:** User must manually activate the device admin via `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` intent.

**Deprecation warning:** Starting Android 9 (API 28), some admin policies are deprecated. Google recommends migrating to managed profiles / Device Owner.

### 3.2 Device Owner (DevicePolicyManager as Device Owner)

Device Owner mode provides **dramatically more control** but requires special provisioning:

**Extra capabilities beyond Device Admin:**

| Capability | API | Notes |
|-----------|-----|-------|
| **Toggle WiFi** | `DevicePolicyManager.setWifiEnabled()` | Works even on API 29+! |
| **Set WiFi configuration** | `DevicePolicyManager.addWifiConfiguration()` | Add/remove networks silently |
| **Silence/unsilence device** | Various restriction APIs | Full control |
| **Install/uninstall apps** | `DevicePolicyManager.installPackage()` | No user prompt |
| **Set time/timezone** | `DevicePolicyManager.setTime()` / `setTimeZone()` | Silent |
| **Disable keyguard (lock screen)** | `setKeyguardDisabled()` | Remove lock screen entirely |
| **Set global settings** | `Settings.Global` writes | WiFi, Bluetooth, Airplane mode |
| **Restrict user actions** | `addUserRestriction()` | Prevent settings changes, app installs |
| **Start activities from background** | Exempted from background activity start restrictions | Can launch any activity anytime |
| **Reboot device** | `DevicePolicyManager.reboot()` | Silent reboot |

### 3.3 How to Become Device Owner

Device Owner can only be set on a **freshly factory-reset device** or via ADB:

```bash
# Via ADB (for development/testing)
adb shell dpm set-device-owner com.yourapp/.admin.MyDeviceAdminReceiver

# Via NFC provisioning (enterprise)
# Tap an NFC tag during initial device setup

# Via QR code (enterprise)
# Scan QR code during initial device setup

# Via Zero-touch enrollment (enterprise)
# Pre-configured via Google/Samsung/OEM portal
```

### 3.4 Viability for Consumer App

| Factor | Assessment |
|--------|-----------|
| **Play Store distribution** | NOT viable — Device Owner apps are not consumer apps |
| **Enterprise/kiosk use** | HIGHLY viable — this is exactly what it's for |
| **Sideloaded consumer app** | Possible but requires factory reset — bad UX |
| **Dedicated device (our use case)** | VIABLE if the device is dedicated to the AI assistant |
| **Personal phone** | NOT viable — requires factory reset, too invasive |

**Recommendation:** Device Owner is only viable if we're building a **dedicated AI assistant device** (like a custom Android kiosk). For a consumer phone app, use AccessibilityService instead.

---

## 4. Android Intent Flags and Tricks

### 4.1 FLAG_ACTIVITY_NEW_TASK

Allows launching activities without a foreground activity context:

```kotlin
fun launchFromService(context: Context, intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
```

**Key behaviors:**
- Required when starting an activity from a Service, BroadcastReceiver, or any non-Activity context
- Creates a new task in the recent apps
- Subject to background activity start restrictions (API 29+)

### 4.2 FLAG_ACTIVITY_NO_ANIMATION + FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

```kotlin
// Launch an activity "invisibly"
intent.addFlags(
    Intent.FLAG_ACTIVITY_NEW_TASK or
    Intent.FLAG_ACTIVITY_NO_ANIMATION or
    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
    Intent.FLAG_ACTIVITY_NO_HISTORY
)
```

Useful for launching a transparent activity that executes an action and immediately finishes.

### 4.3 PendingIntent for Deferred Execution

```kotlin
// Create a PendingIntent that executes later
val intent = Intent(context, SilentActionReceiver::class.java).apply {
    action = "EXECUTE_COMMAND"
    putExtra("command", "set_alarm")
    putExtra("hour", 7)
    putExtra("minutes", 0)
}
val pendingIntent = PendingIntent.getBroadcast(
    context, requestCode, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

// Execute immediately or schedule
pendingIntent.send()
```

**Android 14+ change:** Explicit opt-in required via `ActivityOptions.setPendingIntentBackgroundActivityStartMode()` for PendingIntents that start activities from background.

**Android 15+ change:** Creators of PendingIntents must also explicitly opt in to allow background activity launches.

### 4.4 WorkManager for Scheduled Silent Actions

```kotlin
class SilentActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val action = inputData.getString("action") ?: return Result.failure()
        when (action) {
            "send_sms" -> {
                val number = inputData.getString("number")!!
                val message = inputData.getString("message")!!
                SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            }
            "set_volume" -> {
                val percent = inputData.getInt("percent", 50)
                val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, 0)
            }
        }
        return Result.success()
    }
}

// Schedule a one-time silent action
val workRequest = OneTimeWorkRequestBuilder<SilentActionWorker>()
    .setInputData(workDataOf("action" to "send_sms", "number" to "+1234567890", "message" to "Hello"))
    .setInitialDelay(5, TimeUnit.MINUTES)
    .build()
WorkManager.getInstance(context).enqueue(workRequest)
```

### 4.5 AlarmManager for Exact Scheduled Actions

```kotlin
// Schedule an exact alarm for a future action
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
val intent = Intent(context, SilentActionReceiver::class.java)
val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

// Android 12+ requires SCHEDULE_EXACT_ALARM permission
// Android 14+ this permission is DENIED by default for new installs
if (Build.VERSION.SDK_INT >= 31) {
    if (alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
    }
} else {
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
}
```

### 4.6 Notification Actions That Execute Silently

```kotlin
// Create a notification with silent action buttons
fun createCommandResultNotification(context: Context, title: String, body: String) {
    val channel = NotificationChannel("ai_results", "AI Results", NotificationManager.IMPORTANCE_DEFAULT)
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(channel)

    // "Undo" action that triggers a BroadcastReceiver silently
    val undoIntent = Intent(context, UndoActionReceiver::class.java)
    val undoPending = PendingIntent.getBroadcast(context, 0, undoIntent, PendingIntent.FLAG_IMMUTABLE)

    val notification = NotificationCompat.Builder(context, "ai_results")
        .setSmallIcon(R.drawable.ic_ai)
        .setContentTitle(title)
        .setContentText(body)
        .addAction(R.drawable.ic_undo, "Undo", undoPending)  // Executes silently
        .setAutoCancel(true)
        .build()

    nm.notify(notificationId, notification)
}
```

---

## 5. Android Version Restrictions

### 5.1 Background Execution Restrictions Timeline

| Version | API | Key Restriction |
|---------|-----|----------------|
| **Android 8 (Oreo)** | 26 | Background service limitations. Must use `startForegroundService()` with notification. Background location limits. |
| **Android 9 (Pie)** | 28 | Device Admin deprecation begins. Foreground service requires `FOREGROUND_SERVICE` permission. |
| **Android 10 (Q)** | 29 | **Background activity start restrictions** — apps cannot start activities from background. `WifiManager.setWifiEnabled()` deprecated. Background location requires `ACCESS_BACKGROUND_LOCATION`. |
| **Android 11 (R)** | 30 | Package visibility restrictions (`<queries>` required). Foreground service restrictions from background (with exemptions). One-time permissions for camera/mic/location. |
| **Android 12 (S)** | 31 | Foreground service launch restrictions from background (apps targeting API 31+ cannot start FGS from background except exemptions). Exact alarm restrictions (`SCHEDULE_EXACT_ALARM`). `SpeechRecognizer.createOnDeviceSpeechRecognizer()` added. |
| **Android 13 (T)** | 33 | `POST_NOTIFICATIONS` runtime permission required. Clipboard read shows toast. `BluetoothAdapter.enable()/disable()` deprecated. Foreground service type required in manifest. |
| **Android 14 (U)** | 34 | `SCHEDULE_EXACT_ALARM` denied by default. Foreground service types strictly enforced. Apps can only kill own background processes. PendingIntent background activity launch requires explicit opt-in by sender. |
| **Android 15 (V)** | 35 | PendingIntent creators must also opt in for BAL. Package stopped state changes cancel PendingIntents. OTP redaction from notifications for untrusted NotificationListenerService. |
| **Android 16** | 36 | Strict Mode for background activity launch detection. `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` vs `ALLOW_ALWAYS` distinction. |

### 5.2 Background Activity Start Restrictions (Critical for Our App)

Starting Android 10, apps **cannot** start activities from the background. **Exemptions:**

1. App has a **visible window** (activity in foreground)
2. App has an activity in the **back stack of the foreground task**
3. App has a **system-bound service** (AccessibilityService, VoiceInteractionService, etc.)
4. App is a **Device Policy Controller in device owner mode**
5. App has `SYSTEM_ALERT_WINDOW` permission
6. App receives a **notification PendingIntent** from the system
7. App receives a **PendingIntent from a visible app**
8. App has a service **bound by a visible app** (with `BIND_ALLOW_ACTIVITY_STARTS` flag on API 34+)

**For our AI assistant:** The AccessibilityService exemption (point 3) means our app CAN start activities from the background as long as the AccessibilityService is running. This is the key enabler.

### 5.3 Foreground Service Requirements

Starting Android 14, foreground services must declare a **type** in the manifest:

```xml
<service
    android:name=".AiListenerService"
    android:foregroundServiceType="microphone|specialUse"
    android:exported="false">
</service>
```

Available types: `camera`, `connectedDevice`, `dataSync`, `health`, `location`, `mediaPlayback`, `mediaProjection`, `microphone`, `phoneCall`, `remoteMessaging`, `shortService`, `specialUse`, `systemExempted`.

For our always-listening AI assistant, we'd use:
- `microphone` — for SpeechRecognizer / voice input
- `specialUse` — for AI processing (requires Play Store justification)

### 5.4 Permission Model Per Version

| Permission | Pre-23 | 23-28 | 29 | 30 | 31 | 33 | 34+ |
|-----------|--------|-------|----|----|----|----|-----|
| RECORD_AUDIO | Install-time | Runtime | Runtime | Runtime + one-time option | Same | Same | Same |
| CAMERA | Install-time | Runtime | Runtime | Runtime + one-time | Same | Same | Same |
| SEND_SMS | Install-time | Runtime | Runtime | Same | Same | Same | Same |
| CALL_PHONE | Install-time | Runtime | Runtime | Same | Same | Same | Same |
| POST_NOTIFICATIONS | N/A | N/A | N/A | N/A | N/A | Runtime | Runtime |
| SCHEDULE_EXACT_ALARM | N/A | N/A | N/A | N/A | Special | Special | Denied by default |
| ACCESS_FINE_LOCATION | Install-time | Runtime | Runtime | Runtime | Approx only default | Same | Same |
| BLUETOOTH_CONNECT | N/A | N/A | N/A | N/A | Runtime | Runtime | Runtime |

---

## 6. Command-Only Architecture Pattern

### 6.1 Architecture Overview

The "command-only" pattern means the app has minimal UI — it receives commands (voice or text), processes them through Gemma 4 for intent classification, and executes actions silently, reporting results via notifications or TTS.

```
┌─────────────────────────────────────────────────────────┐
│                    USER INPUT LAYER                      │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │ Voice Input   │  │ Text Input   │  │ Notification  │ │
│  │ (SpeechRec)   │  │ (Chat UI)    │  │ Quick Reply   │ │
│  └──────┬───────┘  └──────┬───────┘  └───────┬───────┘ │
│         │                  │                   │         │
│         └──────────────────┼───────────────────┘         │
│                            ▼                             │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              GEMMA 4 NLU ENGINE                     │ │
│  │  "Send a WhatsApp to Mom saying I'll be late"       │ │
│  │         ▼                                           │ │
│  │  Tool Call: send_whatsapp(contact="Mom",            │ │
│  │            message="I'll be late")                  │ │
│  └─────────────────────┬───────────────────────────────┘ │
│                        ▼                                 │
│  ┌─────────────────────────────────────────────────────┐ │
│  │           ACTION EXECUTOR SERVICE                   │ │
│  │  ┌─────────┐ ┌──────────┐ ┌────────────────────┐   │ │
│  │  │ Silent  │ │ Intent   │ │ AccessibilityService│   │ │
│  │  │ APIs    │ │ Launcher │ │ (UI Automation)     │   │ │
│  │  └─────────┘ └──────────┘ └────────────────────┘   │ │
│  └─────────────────────┬───────────────────────────────┘ │
│                        ▼                                 │
│  ┌─────────────────────────────────────────────────────┐ │
│  │            RESULT REPORTING LAYER                   │ │
│  │  ┌──────────┐  ┌──────────────┐  ┌───────────────┐ │ │
│  │  │ TTS      │  │ Notification │  │ In-App Log    │ │ │
│  │  │ Response │  │ with Status  │  │ (optional)    │ │ │
│  │  └──────────┘  └──────────────┘  └───────────────┘ │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 6.2 Voice Recognition with SpeechRecognizer

```kotlin
class VoiceCommandService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        // Use on-device recognizer for privacy (API 31+)
        speechRecognizer = if (Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull() ?: return
                // Feed command to Gemma 4 for intent parsing
                processCommandWithGemma4(command)
            }

            override fun onError(error: Int) {
                // Restart listening after error
                if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    startListening()
                }
            }

            // ... other callbacks
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }
}
```

**Key notes:**
- `SpeechRecognizer` requires `RECORD_AUDIO` permission
- `createOnDeviceSpeechRecognizer()` (API 31+) keeps audio on-device — perfect for privacy
- Must be called from **main thread**
- For continuous listening, restart after each result/error
- For API 30+, add `<queries>` for `android.speech.RecognitionService`

### 6.3 Gemma 4 NLU + SpeechRecognizer Pipeline

```kotlin
// Voice → Text → Gemma 4 → Tool Call → Execute
fun processCommandWithGemma4(voiceText: String) {
    val prompt = """
    You are an AI assistant. Parse the user's command and output a tool call.
    Available tools: send_sms, set_alarm, set_volume, toggle_flashlight, 
    send_whatsapp, make_call, set_timer, play_music, navigate_to, open_app,
    set_brightness, toggle_dnd, open_url, take_photo, media_control
    
    User command: "$voiceText"
    
    Output JSON: {"tool": "...", "args": {...}}
    """.trimIndent()

    // Run Gemma 4 inference on-device via LiteRT-LM
    val result = gemmaModel.generateContent(prompt)
    val toolCall = parseToolCall(result.text)
    executeToolCall(toolCall)
}
```

### 6.4 How Existing AI Assistants Achieve Silent Execution

| Assistant | Method | Limitations |
|----------|--------|-------------|
| **Google Assistant** | System app with `INTERACT_ACROSS_USERS`, `BIND_APPWIDGET`, and dozens of system permissions. Uses VoiceInteractionService (exempted from BAL restrictions). | Cannot be replicated by 3rd-party apps |
| **Samsung Bixby** | OEM system app with platform-signed certificate. Has `WRITE_SECURE_SETTINGS`. | Cannot be replicated |
| **Tasker** | AccessibilityService + Notification Listener + Device Admin. Users grant all permissions during setup. | Fully replicable by our app |
| **Automate** | Same approach as Tasker — AccessibilityService-based. | Fully replicable |
| **MacroDroid** | AccessibilityService + root (optional) for deeper access | AccessibilityService part is replicable |

**Key insight:** Tasker's approach is the most viable model for our app. It uses:
1. AccessibilityService for UI automation
2. NotificationListenerService for reading notifications
3. Device Admin for lock/wipe capabilities
4. Standard runtime permissions for SMS, calls, etc.
5. A single setup wizard that walks users through granting all permissions

---

## 7. Notification-Based Interaction

### 7.1 Reporting Results via Notifications

Instead of opening activities, the app can report ALL results via notifications:

```kotlin
object AiNotificationManager {
    private const val CHANNEL_COMMANDS = "ai_commands"
    private const val CHANNEL_RESULTS = "ai_results"
    private const val CHANNEL_ERRORS = "ai_errors"
    private const val CHANNEL_LISTENING = "ai_listening"

    fun init(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_COMMANDS, "Command Processing", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_RESULTS, "Action Results", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_ERRORS, "Errors", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_LISTENING, "Listening", NotificationManager.IMPORTANCE_MIN)
        ))
    }

    fun notifyResult(context: Context, title: String, body: String, actions: List<NotificationCompat.Action> = emptyList()) {
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_ai_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)

        actions.forEach { builder.addAction(it) }

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
```

### 7.2 Heads-Up Notifications for Quick Feedback

```kotlin
// High-priority notification that pops up at the top of the screen
fun showHeadsUpResult(context: Context, message: String) {
    val channel = NotificationChannel("ai_urgent", "Urgent Results", NotificationManager.IMPORTANCE_HIGH)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, "ai_urgent")
        .setSmallIcon(R.drawable.ic_ai)
        .setContentTitle("AI Assistant")
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(999, notification)
}
```

### 7.3 Notification Channels by Action Category

| Channel | Importance | Purpose |
|---------|-----------|---------|
| `ai_listening` | MIN | Persistent notification showing service is active |
| `ai_commands` | LOW | Command acknowledged / processing |
| `ai_results` | DEFAULT | Action completed successfully |
| `ai_errors` | HIGH | Action failed, needs attention |
| `ai_urgent` | HIGH | Time-sensitive results (heads-up) |
| `ai_communication` | DEFAULT | SMS sent, call made confirmations |
| `ai_reminders` | HIGH | Alarm/timer/reminder notifications |

### 7.4 Full-Screen Intent for Lock Screen

When the device is locked, you can show a full-screen notification:

```kotlin
val fullScreenIntent = Intent(context, CommandResultActivity::class.java)
val fullScreenPending = PendingIntent.getActivity(context, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE)

val notification = NotificationCompat.Builder(context, "ai_urgent")
    .setSmallIcon(R.drawable.ic_ai)
    .setContentTitle("Alarm!")
    .setContentText("Your 7:00 AM alarm")
    .setFullScreenIntent(fullScreenPending, true)  // Shows on lock screen
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .build()
```

Requires `USE_FULL_SCREEN_INTENT` permission (auto-granted on API 33 and below; user-grantable on API 34+).

---

## 8. Recommended Architecture for Zero-Intervention AI Assistant

### 8.1 First-Run Setup Wizard

The app should have a **one-time setup wizard** that requests all permissions upfront:

```
Step 1: Welcome → Explain what the app does
Step 2: Request RECORD_AUDIO (for voice commands)
Step 3: Request SEND_SMS, CALL_PHONE (for communication)
Step 4: Request READ_CONTACTS, READ_CALENDAR (for context)
Step 5: Request POST_NOTIFICATIONS (for results)
Step 6: Enable AccessibilityService (open Settings)
Step 7: Grant Notification Policy Access (for DND)
Step 8: Grant WRITE_SETTINGS (for brightness)
Step 9: Enable NotificationListenerService (for reading notifications)
Step 10: Set as default SMS app (optional, for silent SMS)
Step 11: Done — all future commands execute silently
```

### 8.2 Service Architecture

```
┌──────────────────────────────────────┐
│       Foreground Service             │
│   (type: microphone|specialUse)      │
│                                      │
│  ┌────────────────────┐              │
│  │ SpeechRecognizer   │──── voice ──→│
│  │ (on-device)        │              │
│  └────────────────────┘              │
│                                      │
│  ┌────────────────────┐              │
│  │ Gemma 4 E2B        │──── NLU ───→│
│  │ (LiteRT-LM)        │              │
│  └────────────────────┘              │
│                                      │
│  ┌────────────────────┐              │
│  │ Action Executor    │──── exec ──→│
│  │ (Silent APIs)      │              │
│  └────────────────────┘              │
└──────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────┐
│    AccessibilityService              │
│    (for UI automation of 3rd-party   │
│     apps like WhatsApp, etc.)        │
└──────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────┐
│    NotificationListenerService       │
│    (for reading incoming messages    │
│     and responding autonomously)     │
└──────────────────────────────────────┘
```

### 8.3 Decision Matrix: Which Execution Path to Use

```
User Command → Gemma 4 Tool Call → ?

IF tool is in Silent API list (volume, flashlight, TTS, alarm, etc.)
  → Execute directly via API
  → Report result via notification

ELSE IF tool requires 3rd-party app UI (WhatsApp, email, etc.)
  → Launch app via intent
  → Use AccessibilityService to auto-fill and auto-tap send
  → Report result via notification

ELSE IF tool requires Settings toggle (WiFi, BT, etc.)
  → Use AccessibilityService to navigate Settings and toggle
  → Report result via notification

ELSE
  → Report to user that manual action is needed
  → Show notification with deep-link to required screen
```

### 8.4 Minimum Android Version Recommendation

For our AI assistant with zero-intervention goal:

- **Minimum:** Android 12 (API 31) — for on-device SpeechRecognizer, Gemma 4 compatibility
- **Recommended:** Android 13 (API 33) — for better notification control, AccessibilityService InputMethod
- **Target:** Android 14 (API 34) — latest stable with foreground service type enforcement

---

*End of research document.*
