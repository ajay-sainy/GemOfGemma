# Command-Action Matrix: Silent Execution Capability

> **Research Date:** April 17, 2026
> **Author:** Elaine (ML Engineer)
> **Purpose:** Comprehensive table of every action, whether it can be silent, permissions needed, API restrictions, and workarounds.

---

## Legend

- **Silent?** — Can execute without ANY user interaction (after initial permission grant)
  - `YES` — Fully silent
  - `SETUP` — Silent after one-time user grant during setup
  - `NO` — Always requires user interaction
  - `PARTIAL` — Some scenarios silent, some not
- **Min API** — Minimum Android API level required
- **Workaround** — How to make non-silent actions silent

---

## Communication Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Send SMS (SmsManager) | SETUP | `SEND_SMS` + default SMS app role | 4 | None needed if default SMS app | Must be default SMS app for fully silent sending |
| Send SMS (Intent) | NO | None | 1 | Use SmsManager instead | Opens compose UI |
| Send WhatsApp message | NO | None | 1 | AccessibilityService: launch chat → set text → tap send | Fragile — WhatsApp UI changes break automation |
| Send Telegram message | NO | None | 1 | AccessibilityService: same approach as WhatsApp | Same fragility concerns |
| Send email (Intent) | NO | None | 1 | AccessibilityService: fill compose → tap send | Varies by email client |
| Make phone call (ACTION_CALL) | SETUP | `CALL_PHONE` | 1 | None needed | Silent once permission granted |
| Make phone call (ACTION_DIAL) | NO | None | 1 | Use ACTION_CALL instead | Opens dialer UI |
| Answer incoming call | SETUP | `ANSWER_PHONE_CALLS` (API 26+) | 26 | AccessibilityService or telecom API | TelecomManager.acceptRingingCall() |
| End phone call | SETUP | `ANSWER_PHONE_CALLS` | 26 | TelecomManager.endCall() | Requires API 28+ |
| Share content | NO | None | 1 | None — system chooser always shown | Cannot bypass chooser |
| Read incoming SMS | SETUP | `RECEIVE_SMS` + `READ_SMS` | 1 | None needed | BroadcastReceiver for SMS_RECEIVED |
| Read call log | SETUP | `READ_CALL_LOG` | 1 | None needed | Direct ContentProvider query |

## Alarm & Reminder Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Set alarm (skip UI) | YES | `SET_ALARM` (normal) | 9 | Use `EXTRA_SKIP_UI = true` | Auto-granted permission |
| Set timer (skip UI) | YES | `SET_ALARM` (normal) | 19 | Use `EXTRA_SKIP_UI = true` | Auto-granted permission |
| Dismiss alarm | NO | None | 28 | AccessibilityService | `AlarmClock.ACTION_DISMISS_ALARM` shows UI |
| Create calendar event (Intent) | NO | None | 14 | Use CalendarProvider API | Opens calendar editor |
| Create calendar event (API) | SETUP | `WRITE_CALENDAR` | 14 | None needed | Silent via ContentResolver insert |
| Read calendar events | SETUP | `READ_CALENDAR` | 14 | None needed | Silent ContentProvider query |
| Create reminder (Intent) | NO | None | 30 | Use calendar event as workaround | Opens reminder UI |
| Schedule WorkManager task | YES | None | 1 | N/A | Always silent, survives reboot |
| Schedule exact alarm | SETUP | `SCHEDULE_EXACT_ALARM` (special) | 31 | Use `USE_EXACT_ALARM` instead | Denied by default on API 34+ |

## Connectivity Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Toggle WiFi | NO | N/A (deprecated API 29+) | 29 | Device Owner: `setWifiEnabled()` OR AccessibilityService: navigate Settings → tap toggle | Only DO/PO and system apps can toggle |
| Toggle Bluetooth | NO | `BLUETOOTH_CONNECT` (API 31+) | 33 | Device Owner OR AccessibilityService | `enable()/disable()` deprecated API 33+ |
| Toggle Airplane Mode | NO | N/A (no API since 17) | 17 | Device Owner: `Settings.Global.AIRPLANE_MODE_ON` OR AccessibilityService | Only system/DO apps |
| Toggle Mobile Data | NO | None | 1 | AccessibilityService on Settings panel | No public API |
| Toggle NFC | NO | None | 16 | AccessibilityService | Opens NFC Settings |
| Toggle Hotspot | NO | None | 1 | AccessibilityService | No direct public API |
| Connect to WiFi network | SETUP | `CHANGE_WIFI_STATE` + location | 29 | `WifiNetworkSuggestion` API (API 29+) | Suggestion API doesn't guarantee connection |
| Open WiFi panel | NO | None | 29 | N/A | `Settings.Panel.ACTION_WIFI` — user toggles |

## Device Settings Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Set volume (any stream) | YES | None | 1 | N/A | Fully silent, no permission needed |
| Set brightness | SETUP | `WRITE_SETTINGS` (special) | 1 | None — user must grant WRITE_SETTINGS once | Silent after grant |
| Toggle flashlight | YES | None | 23 | N/A | `CameraManager.setTorchMode()` — fully silent |
| Toggle DND | SETUP | Notification Policy Access (special) | 23 | None — user must grant once in Settings | Silent after grant |
| Set screen timeout | SETUP | `WRITE_SETTINGS` | 1 | None needed after grant | `Settings.System.SCREEN_OFF_TIMEOUT` |
| Toggle auto-rotate | SETUP | `WRITE_SETTINGS` | 1 | None needed after grant | `Settings.System.ACCELEROMETER_ROTATION` |
| Open any Settings page | NO | None | 1 | N/A — always opens Settings UI | Informational only |
| Set ringtone | SETUP | `WRITE_SETTINGS` | 1 | None needed after grant | `RingtoneManager` |
| Toggle location services | NO | None | 1 | AccessibilityService on Settings | Cannot toggle programmatically |

## Media & Camera Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Play/Pause media | YES | None | 19 | N/A | `AudioManager.dispatchMediaKeyEvent()` — fully silent |
| Skip track (next) | YES | None | 19 | N/A | Same API, `KEYCODE_MEDIA_NEXT` |
| Previous track | YES | None | 19 | N/A | Same API, `KEYCODE_MEDIA_PREVIOUS` |
| Take photo (Intent) | NO | None | 3 | Use Camera2 API directly | Opens camera app |
| Take photo (Camera2) | SETUP | `CAMERA` | 21 | None — capture silently in background | Can capture without showing preview |
| Record video (Intent) | NO | None | 3 | Use Camera2 + MediaRecorder directly | Opens camera app |
| Record audio | SETUP | `RECORD_AUDIO` | 1 | None needed | `MediaRecorder` or `AudioRecord` — silent |
| Play music search | NO | None | 9 | Use `MediaPlayer` directly for local files | Opens music app |
| Text-to-Speech | YES | None | 4 | N/A | `TextToSpeech.speak()` — fully silent (outputs audio) |

## App & Navigation Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Open an app | PARTIAL | `QUERY_ALL_PACKAGES` (API 30+) | 1 | Declare `<queries>` for target packages | Opens app UI — the app becomes visible |
| Open URL in browser | PARTIAL | None | 1 | N/A | Opens browser — not "silent" but no confirmation needed |
| Navigate to address (Maps) | NO | None | 1 | N/A | Opens Maps app |
| Open specific Settings | NO | None | 1 | N/A | Opens Settings UI |
| Search the web | NO | None | 1 | Use in-app WebView or HTTP client | Opens browser/search app |
| Install app | NO | None | 1 | Device Owner only | Always requires user approval |
| Uninstall app | NO | None | 1 | Device Owner only | Always requires user approval |

## Files & Data Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Read clipboard | PARTIAL | None (but restricted API 33+) | 11 | Must be foreground; toast shown on API 33+ | Cannot read silently from background |
| Write clipboard | YES | None | 11 | N/A | Fully silent |
| Read contacts | SETUP | `READ_CONTACTS` | 1 | None needed | Silent ContentProvider query |
| Write contacts | SETUP | `WRITE_CONTACTS` | 1 | None needed | Silent ContentProvider insert |
| Read files (app storage) | YES | None | 1 | N/A | App-specific dirs always accessible |
| Read files (shared storage) | SETUP | `READ_MEDIA_*` (API 33+) or `READ_EXTERNAL_STORAGE` | 1 | Use SAF for user-selected files | Scoped storage restrictions |
| Write files (app storage) | YES | None | 1 | N/A | Always accessible |
| Open a file (Intent) | NO | None | 1 | N/A | Opens file with default app |
| Create a note | NO | None | 34 | Write to app-internal file instead | Opens notes app |
| Download file | YES | None | 1 | `DownloadManager` or `OkHttp` | Silent background download |

## System Actions (via AccessibilityService)

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Go Home | YES | AccessibilityService | 16 | N/A | `performGlobalAction(GLOBAL_ACTION_HOME)` |
| Go Back | YES | AccessibilityService | 16 | N/A | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| Open Notifications | YES | AccessibilityService | 16 | N/A | `performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)` |
| Open Quick Settings | YES | AccessibilityService | 17 | N/A | `performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)` |
| Open Recents | YES | AccessibilityService | 16 | N/A | `performGlobalAction(GLOBAL_ACTION_RECENTS)` |
| Lock Screen | YES | AccessibilityService | 28 | Device Admin `lockNow()` also works | `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` |
| Take Screenshot | YES | AccessibilityService | 28 | N/A | `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)` |
| Read screen content | YES | AccessibilityService | 16 | N/A | `getRootInActiveWindow()` — full UI tree |
| Click any UI element | YES | AccessibilityService | 16 | N/A | `node.performAction(ACTION_CLICK)` |
| Set text in any field | YES | AccessibilityService | 21 | N/A | `node.performAction(ACTION_SET_TEXT, bundle)` |
| Dispatch touch gesture | YES | AccessibilityService | 24 | N/A | `dispatchGesture()` — tap, swipe, etc. |
| Intercept key events | YES | AccessibilityService | 18 | N/A | `onKeyEvent()` callback |
| Take window screenshot | YES | AccessibilityService | 30 | N/A | `takeScreenshot()` / `takeScreenshotOfWindow()` |

## Notification Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Post notification | SETUP | `POST_NOTIFICATIONS` (API 33+) | 1 | Auto-granted below API 33 | Silent after grant |
| Cancel notification | YES | None | 1 | N/A | Always silent |
| Read other apps' notifications | SETUP | NotificationListenerService (special) | 18 | User must enable in Settings | Can read all notification content |
| Full-screen intent (lock screen) | SETUP | `USE_FULL_SCREEN_INTENT` | 11 | Auto-granted API ≤33; user-grantable API 34+ | Shows activity on lock screen |
| Heads-up notification | SETUP | `POST_NOTIFICATIONS` + HIGH importance | 21 | None needed | Pops up at top of screen |

## Device Admin Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Lock device | SETUP | Device Admin | 8 | AccessibilityService `GLOBAL_ACTION_LOCK_SCREEN` | `DevicePolicyManager.lockNow()` |
| Wipe device data | SETUP | Device Admin | 8 | None | `DevicePolicyManager.wipeData()` — DESTRUCTIVE |
| Disable camera | SETUP | Device Admin | 14 | N/A | `setCameraDisabled()` |
| Set password policy | SETUP | Device Admin | 8 | N/A | Various password quality methods |
| Toggle WiFi (DO only) | SETUP | Device Owner | 29 | N/A | Only for Device Owner apps |
| Set time/timezone (DO) | SETUP | Device Owner | 28 | N/A | `setTime()` / `setTimeZone()` |
| Install app silently (DO) | SETUP | Device Owner | 21 | N/A | No user prompt |
| Reboot device (DO) | SETUP | Device Owner | 24 | N/A | `DevicePolicyManager.reboot()` |

## Voice & AI Actions

| Action | Silent? | Permission | Min API | Workaround | Notes |
|--------|---------|-----------|---------|------------|-------|
| Listen for voice (SpeechRecognizer) | SETUP | `RECORD_AUDIO` | 8 | None needed | Listens silently, returns text |
| On-device speech recognition | SETUP | `RECORD_AUDIO` | 31 | Use cloud recognizer on older APIs | `createOnDeviceSpeechRecognizer()` |
| Run Gemma 4 inference | YES | None | 31 (LiteRT-LM) | N/A | On-device, no network needed |
| Language detection | YES | None | 34 | N/A | `EXTRA_ENABLE_LANGUAGE_DETECTION` with SpeechRecognizer |

---

## Summary Statistics

| Category | Total Actions | Fully Silent | Silent After Setup | Always Interactive |
|----------|--------------|-------------|-------------------|-------------------|
| Communication | 12 | 0 | 6 | 6 |
| Alarms & Reminders | 9 | 3 | 3 | 3 |
| Connectivity | 8 | 0 | 1 | 7 |
| Device Settings | 9 | 2 | 5 | 2 |
| Media & Camera | 9 | 4 | 3 | 2 |
| Apps & Navigation | 7 | 0 | 0 | 7 |
| Files & Data | 10 | 4 | 3 | 3 |
| System (A11y) | 12 | 12 | 0 | 0 |
| Notifications | 5 | 1 | 4 | 0 |
| Device Admin | 8 | 0 | 8 | 0 |
| Voice & AI | 4 | 2 | 2 | 0 |
| **TOTAL** | **93** | **28** | **35** | **30** |

**Key takeaway:** 63 out of 93 actions (68%) can execute without user interaction after initial setup. With AccessibilityService, we can push this to ~85% by automating UI interactions in 3rd-party apps.

---

## Recommended First-Run Permission Request Order

1. `RECORD_AUDIO` — voice commands (runtime)
2. `SEND_SMS` — messaging (runtime)
3. `CALL_PHONE` — phone calls (runtime)
4. `READ_CONTACTS` — contact lookup (runtime)
5. `READ_CALENDAR` / `WRITE_CALENDAR` — calendar (runtime)
6. `POST_NOTIFICATIONS` — result reporting (runtime, API 33+)
7. `CAMERA` — photo capture (runtime)
8. `ACCESS_FINE_LOCATION` — location queries (runtime)
9. Enable **AccessibilityService** (Settings redirect)
10. Grant **Notification Policy Access** (Settings redirect)
11. Grant **WRITE_SETTINGS** (Settings redirect)
12. Enable **NotificationListenerService** (Settings redirect)
13. Set as **default SMS app** (optional, Settings redirect)

Total user taps during setup: ~15-20 (one-time only)

---

*End of command-action matrix.*
