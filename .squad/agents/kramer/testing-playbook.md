# ADB Testing Playbook

Quick reference for testing GemOfGemma on a physical device via ADB.

## Environment Setup

```powershell
# Required env vars (set in your shell profile)
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17..."  # or your JDK path
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# ADB lives at:
# $env:ANDROID_HOME\platform-tools\adb.exe

# Verify device is connected
adb devices
```

If multiple devices are attached, use `adb -s <serial>` for all commands.

## Build → Install → Test Loop

```powershell
# Build the debug APK
.\gradlew :app:assembleDebug

# Install on device (replace existing)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch the app
adb shell am start -n com.gemofgemma/.MainActivity
```

## Screenshots

```powershell
# Capture screenshot on device
adb shell screencap -p /sdcard/screen.png

# Pull to local machine
adb pull /sdcard/screen.png .\screenshot.png

# Clean up device file
adb shell rm /sdcard/screen.png
```

View the pulled PNG in VS Code or any image viewer to visually verify UI state.

## UI Hierarchy Inspection

```powershell
# Dump the current UI tree
adb shell uiautomator dump /sdcard/ui-dump.xml
adb pull /sdcard/ui-dump.xml .\ui-dump.xml
```

The XML contains every visible element with:
- `text` — displayed text (e.g., "Chat", "Settings")
- `content-desc` — accessibility label (e.g., "Voice input")
- `bounds` — pixel coordinates as `[left,top][right,bottom]`
- `clickable` / `focusable` — whether it's a tap target

**Finding tap coordinates:** Parse `bounds="[x1,y1][x2,y2]"` and compute center:
- `tap_x = (x1 + x2) / 2`
- `tap_y = (y1 + y2) / 2`

## Interaction via ADB

```powershell
# Tap at coordinates (computed from bounds)
adb shell input tap <x> <y>

# Type text into a focused field
adb shell input text "hello"

# Press back button
adb shell input keyevent KEYCODE_BACK

# Press enter
adb shell input keyevent KEYCODE_ENTER
```

**Bottom nav tabs** (approximate center-of-bounds approach):
- Find the tab's `bounds` in the UI dump
- Compute center coordinates
- `adb shell input tap <center_x> <center_y>`

Coordinates are device-specific. Always re-dump the UI hierarchy if the layout changes.

## What to Verify

### Chat Screen
- [ ] Title bar shows "GemOfGemma"
- [ ] Message input field is visible with placeholder text
- [ ] Voice input button is present
- [ ] Sent messages appear in chat bubbles
- [ ] AI responses render (check for text content in UI dump)

### Camera Screen
- [ ] Camera preview is live (screenshot shows camera feed, not black)
- [ ] Detection overlays render on objects
- [ ] Tab navigation to/from Camera works

### Settings Screen
- [ ] Settings options are visible and tappable
- [ ] Toggle states persist after leaving and returning

### Onboarding
- [ ] First launch shows onboarding flow
- [ ] Permissions are requested correctly
- [ ] Can complete onboarding and reach main screen

### General
- [ ] Bottom nav has 3 tabs: Chat, Camera, Settings
- [ ] Tab switching works in all directions
- [ ] No crashes (check `adb logcat *:E` for fatal errors)
- [ ] App survives backgrounding and foregrounding

## Quick Logcat

```powershell
# Filter for app errors
adb logcat --pid=$(adb shell pidof com.gemofgemma) *:E

# Filter for app output (all levels)
adb logcat --pid=$(adb shell pidof com.gemofgemma)
```
