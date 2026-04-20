# GemOfGemma — Architecture Document

**Author:** Jerry (Lead Architect)  
**Date:** April 17, 2026  
**Status:** Architecture Complete — Ready for Implementation  
**Based on:** Research docs 00–09 by Elaine (ML Engineer)

---

## 1. Architecture Overview

GemOfGemma is a layered, on-device AI assistant built on Gemma 4 E2B (2.58 GB, Apache 2.0). Every layer has a single responsibility and communicates through well-defined interfaces.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PRESENTATION LAYER                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ Chat UI  │  │ Camera   │  │ Settings │  │ Overlay / Bounding│  │
│  │ (Compose)│  │ Viewfind │  │ (Compose)│  │ Box Canvas        │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────────┬──────────┘  │
│       │              │              │                 │             │
│  ┌────┴──────────────┴──────────────┴─────────────────┴──────────┐  │
│  │              VIEWMODELS (StateFlow → Compose)                 │  │
│  └───────────────────────────┬───────────────────────────────────┘  │
├──────────────────────────────┼──────────────────────────────────────┤
│                         INPUT LAYER                                │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐     │
│  │ CameraX      │  │ SpeechRec    │  │ Text / Notification   │     │
│  │ (frames)     │  │ (voice→text) │  │ (chat input, replies) │     │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬───────────┘     │
│         │                  │                      │                 │
│         └──────────────────┼──────────────────────┘                 │
│                            ▼                                       │
├────────────────────────────────────────────────────────────────────-┤
│                          AI LAYER                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   GemmaService (Foreground)                 │   │
│  │  ┌───────────┐  ┌────────────┐  ┌────────────────────────┐ │   │
│  │  │ LiteRT-LM │  │ Prompt     │  │ Response Parser        │ │   │
│  │  │ Engine    │  │ Router     │  │ (JSON, bbox, text)     │ │   │
│  │  └───────────┘  └────────────┘  └────────────────────────┘ │   │
│  │  ┌───────────────────────────────────────────────────────┐  │   │
│  │  │ Function-Calling Schema (Tool Definitions)            │  │   │
│  │  └───────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────┬───────────────────────────────────┘   │
│                            ▼                                       │
├────────────────────────────────────────────────────────────────────-┤
│                        ACTION LAYER                                │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐     │
│  │ Silent API   │  │ Intent       │  │ AccessibilityService  │     │
│  │ Executor     │  │ Dispatcher   │  │ Bridge                │     │
│  │ (vol, torch) │  │ (SMS, call)  │  │ (UI automation)       │     │
│  └──────────────┘  └──────────────┘  └───────────────────────┘     │
│                            ▼                                       │
├────────────────────────────────────────────────────────────────────-┤
│                       FEEDBACK LAYER                               │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐     │
│  │ TTS          │  │ Notification │  │ In-App Status         │     │
│  │ (spoken)     │  │ (result)     │  │ (chat bubble)         │     │
│  └──────────────┘  └──────────────┘  └───────────────────────┘     │
└────────────────────────────────────────────────────────────────────-┘
```

**Key Principles:**
- **On-device only** — zero data leaves the device. No cloud calls for AI inference.
- **Single model** — Gemma 4 E2B handles vision, NLU, function-calling, and text generation.
- **Foreground service** — GemmaService keeps the model loaded and warm in memory.
- **Permission-gated execution** — Silent actions after one-time setup; user-confirming UI for dangerous actions.
- **Separation of concerns** — UI knows nothing about model internals; action layer knows nothing about UI.

---

## 2. Module Breakdown

### 2.1 Module Dependency Graph

```
:app ──→ :ui ──→ :camera
  │       │         │
  │       ▼         │
  │     :voice      │
  │       │         │
  └──→ :ai ◄───────┘
        │
        ▼
     :actions ──→ :accessibility
        │
        ▼
     :core (shared models, DI, utils)
```

### 2.2 Module Details

| Module | Package | Contains | Owner | Dependencies |
|--------|---------|----------|-------|--------------|
| **:app** | `com.gemofgemma` | `Application`, Hilt setup, `MainActivity`, navigation host, permission orchestration, foreground service lifecycle | Jerry (scaffold) → George (implementation) | All modules |
| **:core** | `com.gemofgemma.core` | Shared data classes (`DetectionResult`, `ChatMessage`, `ActionResult`), DI qualifiers, coroutine dispatchers, extension functions | Jerry | None |
| **:ui** | `com.gemofgemma.ui` | Compose screens (Chat, Camera, Settings, Onboarding), navigation graph, theme, reusable components (bounding box overlay, chat bubbles) | George | `:core`, `:ai` (via ViewModel interfaces) |
| **:camera** | `com.gemofgemma.camera` | CameraX setup, frame capture, image preprocessing (resize, byte conversion), camera permission handling | George | `:core` |
| **:ai** | `com.gemofgemma.ai` | `GemmaService` (foreground service), `GemmaEngine` (LiteRT-LM wrapper), prompt templates, response parsers (JSON bbox, text, function calls), tool/function definitions, model download manager | Elaine | `:core` |
| **:voice** | `com.gemofgemma.voice` | `SpeechRecognizer` wrapper, on-device vs cloud recognizer selection, continuous listening lifecycle, wake-word placeholder, TTS feedback | George | `:core` |
| **:actions** | `com.gemofgemma.actions` | `ActionDispatcher` (routes function calls to handlers), individual action handlers (SMS, call, alarm, volume, flashlight, DND, media controls, app launch, navigation), permission checker, safety validator | Elaine | `:core`, `:accessibility` |
| **:accessibility** | `com.gemofgemma.accessibility` | `GemOfGemmaAccessibilityService`, UI tree reader, auto-tap executor, global action performer (home, back, lock), AccessibilityService lifecycle | Elaine | `:core` |

### 2.3 Ownership Summary

| Team Member | Modules | Focus |
|-------------|---------|-------|
| **George** (UI/UX) | `:ui`, `:camera`, `:voice` | Everything the user sees and hears |
| **Elaine** (ML Engineer) | `:ai`, `:actions`, `:accessibility` | Everything the AI does and controls |
| **Kramer** (QA) | Test suites across all modules | Integration tests, E2E scenarios, edge cases |
| **Jerry** (Lead) | `:app`, `:core` (scaffold) | Architecture enforcement, PR reviews |

---

## 3. Core Pipeline Design

### 3.1 Input Layer

Three input sources feed into the AI layer through a unified `AiRequest` sealed class:

```kotlin
// :core
sealed class AiRequest {
    data class TextChat(val message: String, val conversationId: String) : AiRequest()
    data class VisionQuery(val imageBytes: ByteArray, val prompt: String) : AiRequest()
    data class VoiceCommand(val transcribedText: String) : AiRequest()
}
```

| Source | Component | How It Works |
|--------|-----------|-------------|
| **Camera** | CameraX `ImageAnalysis` + `ImageCapture` | Captures frames as `ByteArray`. For detection: periodic frame analysis. For photo queries: user-triggered capture. |
| **Voice** | `SpeechRecognizer` (on-device, API 31+) | Converts speech to text. Text is wrapped as `AiRequest.VoiceCommand` and sent to the AI layer. Uses `createOnDeviceSpeechRecognizer()` for privacy. |
| **Text** | Compose `TextField` in Chat UI | User types a message. Wrapped as `AiRequest.TextChat`. |

### 3.2 AI Layer — GemmaService

`GemmaService` is a **bound foreground service** that:
1. Loads Gemma 4 E2B via LiteRT-LM on startup (~10s, GPU backend)
2. Maintains a warm `Engine` instance in memory (~676 MB GPU)
3. Exposes a `suspend fun process(request: AiRequest): AiResponse` API
4. Routes requests through prompt templates based on request type
5. Parses responses into structured types

```kotlin
// :ai
class GemmaService : LifecycleService() {
    private lateinit var engine: Engine
    private val binder = GemmaBinder()

    inner class GemmaBinder : Binder() {
        fun getService(): GemmaService = this@GemmaService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Initialize on IO dispatcher
        lifecycleScope.launch(Dispatchers.IO) {
            engine = Engine(EngineConfig(
                modelPath = getModelPath(),
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                cacheDir = cacheDir.path
            ))
            engine.initialize()
            // Pre-warm with dummy query
            engine.createConversation(ConversationConfig()).use {
                it.sendMessage("Hello")
            }
        }
    }

    suspend fun process(request: AiRequest): AiResponse {
        return withContext(Dispatchers.IO) {
            when (request) {
                is AiRequest.TextChat -> processChat(request)
                is AiRequest.VisionQuery -> processVision(request)
                is AiRequest.VoiceCommand -> processVoiceCommand(request)
            }
        }
    }
}
```

#### Prompt Router

The prompt router selects the right system prompt and conversation config based on the request type:

| Request Type | System Prompt Strategy | Function-Calling? |
|-------------|----------------------|-------------------|
| `TextChat` | General assistant persona | No (unless user asks to do something) |
| `VisionQuery` (detection) | "Detect all objects, return JSON `[{box_2d, label}]`" | No |
| `VisionQuery` (captioning) | "Describe this image in detail" | No |
| `VisionQuery` (OCR) | "Extract all text from this image" | No |
| `VoiceCommand` | Assistant with tool definitions registered — NLU + function-calling | **Yes** |

#### Function-Calling Schema

For voice commands and action-triggering chat, the AI layer registers tools via LiteRT-LM's `ToolSet` API:

```kotlin
// :ai
class PhoneActionToolSet : ToolSet {
    @Tool(description = "Send an SMS text message to a contact")
    fun sendSms(phoneNumber: String, message: String): Map<String, Any> { ... }

    @Tool(description = "Make a phone call to a contact")
    fun makeCall(phoneNumber: String): Map<String, Any> { ... }

    @Tool(description = "Set an alarm for a specific time")
    fun setAlarm(hour: Int, minutes: Int, label: String): Map<String, Any> { ... }

    @Tool(description = "Set a countdown timer")
    fun setTimer(durationSeconds: Int, label: String): Map<String, Any> { ... }

    @Tool(description = "Toggle the flashlight on or off")
    fun toggleFlashlight(on: Boolean): Map<String, Any> { ... }

    @Tool(description = "Set media volume to a percentage 0-100")
    fun setVolume(percent: Int): Map<String, Any> { ... }

    @Tool(description = "Toggle Do Not Disturb mode")
    fun toggleDnd(enabled: Boolean): Map<String, Any> { ... }

    @Tool(description = "Open an app by name")
    fun openApp(appName: String): Map<String, Any> { ... }

    @Tool(description = "Navigate to an address or place")
    fun navigate(destination: String): Map<String, Any> { ... }

    @Tool(description = "Control media playback: play, pause, next, previous")
    fun mediaControl(action: String): Map<String, Any> { ... }

    @Tool(description = "Set screen brightness to a percentage 0-100")
    fun setBrightness(percent: Int): Map<String, Any> { ... }

    @Tool(description = "Create a calendar event")
    fun createCalendarEvent(title: String, startTime: String, endTime: String, location: String): Map<String, Any> { ... }
}
```

### 3.3 Action Layer — ActionDispatcher

The `ActionDispatcher` receives parsed function calls from the AI layer and routes them to the correct handler:

```kotlin
// :actions
class ActionDispatcher @Inject constructor(
    private val smsHandler: SmsActionHandler,
    private val callHandler: CallActionHandler,
    private val alarmHandler: AlarmActionHandler,
    private val volumeHandler: VolumeActionHandler,
    private val flashlightHandler: FlashlightActionHandler,
    private val dndHandler: DndActionHandler,
    private val appLaunchHandler: AppLaunchHandler,
    private val navigationHandler: NavigationHandler,
    private val mediaHandler: MediaActionHandler,
    private val brightnessHandler: BrightnessActionHandler,
    private val calendarHandler: CalendarActionHandler,
    private val accessibilityBridge: AccessibilityBridge,
    private val safetyValidator: SafetyValidator,
) {
    suspend fun dispatch(action: ParsedAction): ActionResult {
        // 1. Validate safety (no destructive actions without confirmation)
        safetyValidator.validate(action)

        // 2. Check required permissions
        if (!action.handler.hasPermissions()) {
            return ActionResult.PermissionRequired(action.requiredPermissions)
        }

        // 3. Execute
        return action.handler.execute(action.params)
    }
}
```

#### Safety Validator

The `SafetyValidator` is a critical guardrail:

```kotlin
// :actions
class SafetyValidator {
    // Actions that ALWAYS require user confirmation before execution
    private val confirmationRequired = setOf(
        "sendSms",       // Sends a message on user's behalf
        "makeCall",      // Initiates a call
        "wipeData",      // Destructive
    )

    // Actions that are blocked entirely
    private val blocked = setOf(
        "wipeData",      // Too dangerous for AI-triggered
        "installApp",    // Security risk
        "uninstallApp",  // Security risk
    )

    fun validate(action: ParsedAction): ValidationResult {
        if (action.name in blocked) return ValidationResult.Blocked
        if (action.name in confirmationRequired) return ValidationResult.NeedsConfirmation(action)
        return ValidationResult.Approved
    }
}
```

### 3.4 Feedback Layer

All action results flow back to the user through three channels:

| Channel | When Used | Component |
|---------|-----------|-----------|
| **TTS** | Voice command results (app in background) | `TextToSpeech` |
| **Notification** | Action completed while app is not visible | `NotificationManager` |
| **In-app chat** | User is in the chat/camera UI | Compose chat bubble via `StateFlow` |

### 3.5 How the Three Layers Connect

```
Input Layer                    AI Layer                      Action Layer
─────────────────────────────────────────────────────────────────────────
CameraX frame ──→ ByteArray ──→ GemmaService.process() ──→ (no action,
                                  ↓                          returns
                                VisionQuery prompt            DetectionResult)
                                  ↓                              ↓
                                Parse JSON bbox ──────────→ UI overlay

SpeechRecognizer ──→ text ────→ GemmaService.process() ──→ Function call
                                  ↓                          parsed
                                VoiceCommand prompt            ↓
                                  ↓                       ActionDispatcher
                                Tool call output ─────────→ .dispatch()
                                                               ↓
                                                          ActionResult
                                                               ↓
                                                          Feedback Layer

TextField ──→ text ───────────→ GemmaService.process() ──→ text response
                                  ↓                              ↓
                                TextChat prompt            chat bubble
```

---

## 4. Data Flow Diagrams

### 4.1 Voice Command → Phone Automation

**Scenario:** "Send SMS to Mom saying I'll be late"

```
┌────────┐    ┌────────────────┐    ┌────────────┐    ┌─────────────┐    ┌──────────────┐
│  User  │───→│ SpeechRecognizer│───→│  Gemma 4   │───→│   Action    │───→│   Feedback   │
│ speaks │    │ (on-device)    │    │  E2B       │    │ Dispatcher  │    │   Layer      │
└────────┘    └────────────────┘    └────────────┘    └─────────────┘    └──────────────┘
                    │                      │                  │                  │
                    │ "send sms to        │ Tool call:       │ SmsManager       │ TTS: "SMS sent
                    │  mom saying         │ sendSms(         │ .sendText(       │  to Mom"
                    │  i'll be late"      │  "+1555...",     │  "+1555...",     │
                    │                     │  "I'll be late") │  "I'll be late") │ Notification:
                    │                     │                  │                  │ "SMS sent ✓"
                    ▼                     ▼                  ▼                  ▼
              text string           ParsedAction         ActionResult       User informed
```

**Detail Steps:**
1. `SpeechRecognizer.onResults()` → `"send sms to mom saying i'll be late"`
2. Text wrapped as `AiRequest.VoiceCommand`
3. `GemmaService` creates conversation with `PhoneActionToolSet` registered
4. Gemma 4 identifies intent and outputs: `sendSms(phoneNumber="Mom", message="I'll be late")`
5. LiteRT-LM tool framework intercepts the call → `ActionDispatcher.dispatch()`
6. `SafetyValidator` flags SMS as `NeedsConfirmation`
7. UI shows confirmation dialog: "Send 'I'll be late' to Mom (+1555...)?"
8. User confirms → `SmsActionHandler.execute()` → `SmsManager.sendTextMessage()`
9. `ActionResult.Success` → TTS speaks "SMS sent to Mom" + notification posted

### 4.2 Camera → Object Detection → Bounding Box Overlay

**Scenario:** User points camera at a scene, taps "Detect"

```
┌──────────┐    ┌──────────┐    ┌────────────┐    ┌──────────────┐    ┌─────────────┐
│ CameraX  │───→│  Frame   │───→│  Gemma 4   │───→│  JSON Parse  │───→│  Canvas     │
│ Preview  │    │ Capture  │    │  E2B       │    │  (bbox list) │    │  Overlay    │
└──────────┘    └──────────┘    └────────────┘    └──────────────┘    └─────────────┘
      │               │               │                  │                  │
      │ Live          │ JPEG bytes    │ "[{box_2d:      │ List<Detection   │ Draw Rect()
      │ preview       │ (resize to    │  [y1,x1,y2,x2],│  Result>         │ with labels
      │               │  token budget │  label:'person' │ rescaled to      │ on camera
      │               │  ~560)        │  }]"            │ image dims       │ preview
      ▼               ▼               ▼                  ▼                  ▼
  Viewfinder      ByteArray     text response        Rect + label      User sees
                                                                       boxes
```

**Detail Steps:**
1. `CameraX` `ImageCapture.takePicture()` on user tap → JPEG `ByteArray`
2. Wrapped as `AiRequest.VisionQuery(imageBytes, "detect all objects")`
3. `GemmaService.process()` creates conversation with system prompt:
   ```
   Detect all objects in this image. Return only JSON array:
   [{"box_2d": [y1, x1, y2, x2], "label": "name"}]
   ```
4. `Contents.of(Content.ImageBytes(bytes), Content.Text(prompt))`
5. Response: raw JSON text
6. `DetectionResponseParser` extracts `List<DetectionResult>`
7. Each `box_2d` coordinate rescaled from 1000×1000 → actual image dimensions
8. `StateFlow<List<DetectionResult>>` emits to UI
9. Compose `Canvas` draws `Rect` + label text over camera preview

### 4.3 Camera → OCR → Text Extraction

**Scenario:** User photographs a sign or document

```
┌──────────┐    ┌──────────┐    ┌────────────┐    ┌──────────────┐
│ CameraX  │───→│  Frame   │───→│  Gemma 4   │───→│  Text Card   │
│ Capture  │    │  Capture │    │  E2B       │    │  (Compose)   │
└──────────┘    └──────────┘    └────────────┘    └──────────────┘
                     │               │                  │
                     │ High-res      │ System prompt:   │ Display
                     │ JPEG bytes    │ "Extract all     │ extracted text,
                     │ (token budget │  text from this  │ copy-to-clipboard
                     │  1120 for OCR)│  image"          │ action
                     ▼               ▼                  ▼
                  ByteArray      plain text          Selectable
                                 response            text card
```

**Detail Steps:**
1. User taps "OCR" mode button → triggers `ImageCapture`
2. High-resolution capture (token budget 1120 for best OCR accuracy)
3. System prompt: `"Extract all visible text from this image. Preserve formatting."`
4. Gemma 4 returns plain text with the extracted content
5. Text displayed in a scrollable, selectable Compose card
6. "Copy" and "Share" action buttons available

### 4.4 Text Chat → AI Response

**Scenario:** User types a question in the chat interface

```
┌──────────┐    ┌──────────────┐    ┌────────────┐    ┌──────────────┐
│ TextField│───→│  ChatViewModel│───→│  Gemma 4   │───→│  Chat Bubble │
│ (Compose)│    │  (StateFlow) │    │  E2B       │    │  (streaming) │
└──────────┘    └──────────────┘    └────────────┘    └──────────────┘
      │               │                   │                  │
      │ "What is      │ AiRequest         │ Streaming        │ Token-by-token
      │  Kotlin?"     │ .TextChat(msg)    │ response via     │ display in
      │               │                   │ Flow<String>     │ chat bubble
      ▼               ▼                   ▼                  ▼
   user input     emit loading        collect tokens      animated text
```

**Detail Steps:**
1. User types message, taps send
2. `ChatViewModel` emits `ChatUiState.Loading`
3. `AiRequest.TextChat` sent to `GemmaService`
4. `conversation.sendMessageAsync(message)` returns `Flow<Message>`
5. ViewModel collects tokens and emits `ChatUiState.Streaming(partialText)`
6. Compose chat bubble renders incrementally
7. On completion: `ChatUiState.Complete(fullText)`
8. Conversation history maintained for multi-turn context

---

## 5. Android Project Structure

```
com.gemofgemma/
├── app/
│   ├── GemOfGemmaApp.kt               — @HiltAndroidApp Application
│   ├── MainActivity.kt                — Single activity, Compose host
│   ├── MainNavGraph.kt                 — NavHost with Compose Navigation
│   ├── PermissionOrchestrator.kt       — First-run permission flow
│   └── di/
│       └── AppModule.kt               — Top-level Hilt bindings
│
├── core/
│   ├── model/
│   │   ├── AiRequest.kt               — Sealed class: TextChat, VisionQuery, VoiceCommand
│   │   ├── AiResponse.kt              — Sealed class: TextResponse, DetectionResponse, ActionResponse
│   │   ├── DetectionResult.kt         — box: Rect, label: String
│   │   ├── ChatMessage.kt             — role, content, timestamp
│   │   └── ActionResult.kt            — Success, Error, PermissionRequired, NeedsConfirmation
│   ├── util/
│   │   ├── CoroutineDispatchers.kt     — Injectable dispatcher provider
│   │   └── Extensions.kt              — Common extensions
│   └── di/
│       └── CoreModule.kt              — Dispatcher bindings
│
├── ui/
│   ├── chat/
│   │   ├── ChatScreen.kt              — Chat UI with message list + input
│   │   ├── ChatViewModel.kt           — Drives chat state
│   │   └── ChatBubble.kt              — Individual message rendering
│   ├── camera/
│   │   ├── CameraScreen.kt            — Camera preview + mode selector (Detect/OCR/Caption)
│   │   ├── CameraViewModel.kt         — Drives camera state + detection results
│   │   ├── BoundingBoxOverlay.kt       — Canvas composable for drawing boxes
│   │   └── OcrResultCard.kt           — Card displaying extracted text
│   ├── settings/
│   │   ├── SettingsScreen.kt           — App settings (model, permissions, about)
│   │   └── SettingsViewModel.kt
│   ├── onboarding/
│   │   ├── OnboardingScreen.kt         — First-run permission + model download
│   │   └── OnboardingViewModel.kt
│   ├── components/
│   │   ├── ConfirmationDialog.kt       — "Are you sure?" for dangerous actions
│   │   ├── LoadingIndicator.kt
│   │   └── PermissionCard.kt
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   └── navigation/
│       └── Screen.kt                  — Route sealed class
│
├── camera/
│   ├── CameraManager.kt               — CameraX lifecycle setup
│   ├── FrameCapture.kt                — ImageAnalysis frame extraction
│   ├── ImagePreprocessor.kt            — Resize, compress, convert to ByteArray
│   └── di/
│       └── CameraModule.kt
│
├── ai/
│   ├── GemmaService.kt                — Foreground service hosting LiteRT-LM Engine
│   ├── GemmaEngine.kt                 — Wrapper around Engine: init, process, close
│   ├── GemmaServiceBinder.kt          — Service binder interface
│   ├── prompt/
│   │   ├── PromptRouter.kt            — Select system prompt by request type
│   │   ├── PromptTemplates.kt         — Detection, OCR, caption, chat prompts
│   │   └── FunctionCallingConfig.kt   — Tool definitions for PhoneActionToolSet
│   ├── parser/
│   │   ├── ResponseParser.kt          — Interface for parsing model output
│   │   ├── DetectionParser.kt         — JSON → List<DetectionResult> + coordinate rescaling
│   │   ├── TextParser.kt              — Plain text extraction
│   │   └── FunctionCallParser.kt      — Tool call → ParsedAction
│   ├── download/
│   │   ├── ModelDownloadManager.kt    — Download .litertlm from HuggingFace
│   │   ├── ModelDownloadWorker.kt     — WorkManager-based download with progress
│   │   └── ModelStatus.kt             — NotDownloaded, Downloading(%), Ready, Error
│   └── di/
│       └── AiModule.kt
│
├── voice/
│   ├── VoiceRecognizer.kt             — SpeechRecognizer wrapper (on-device API 31+)
│   ├── VoiceListenerService.kt        — Continuous listening lifecycle (optional)
│   ├── TtsFeedback.kt                 — TextToSpeech wrapper for spoken responses
│   └── di/
│       └── VoiceModule.kt
│
├── actions/
│   ├── ActionDispatcher.kt            — Routes ParsedAction → handler
│   ├── SafetyValidator.kt             — Blocks/confirms dangerous actions
│   ├── PermissionChecker.kt           — Checks all required permissions for an action
│   ├── handlers/
│   │   ├── SmsActionHandler.kt
│   │   ├── CallActionHandler.kt
│   │   ├── AlarmActionHandler.kt
│   │   ├── TimerActionHandler.kt
│   │   ├── VolumeActionHandler.kt
│   │   ├── FlashlightActionHandler.kt
│   │   ├── DndActionHandler.kt
│   │   ├── BrightnessActionHandler.kt
│   │   ├── MediaActionHandler.kt
│   │   ├── AppLaunchHandler.kt
│   │   ├── NavigationHandler.kt
│   │   ├── CalendarActionHandler.kt
│   │   └── ClipboardActionHandler.kt
│   └── di/
│       └── ActionsModule.kt
│
├── accessibility/
│   ├── GemOfGemmaAccessibilityService.kt  — AccessibilityService implementation
│   ├── AccessibilityBridge.kt             — API for action layer to request UI automation
│   ├── UiTreeReader.kt                    — Read UI elements from other apps
│   ├── AutoTapExecutor.kt                 — Find and click elements by text/description
│   └── di/
│       └── AccessibilityModule.kt
│
└── res/
    ├── xml/
    │   └── accessibility_service_config.xml
    └── values/
        └── strings.xml
```

---

## 6. Technology Decisions

### 6.1 Dependency Injection — Hilt

**Choice:** Hilt (over Koin)  
**Rationale:**
- Compile-time DI graph validation — catches wiring errors at build, not runtime
- First-party Google library with official Android support
- `@HiltAndroidApp`, `@AndroidEntryPoint` simplify Service/Activity injection
- ViewModel injection via `@HiltViewModel` is seamless with Compose
- Koin is simpler for small projects, but GemOfGemma has 8 modules with complex dependency graphs — Hilt's compile-time safety is worth the boilerplate

### 6.2 Navigation — Compose Navigation

**Choice:** Compose Navigation (Jetpack)  
**Rationale:**
- Single-Activity architecture with Compose screens
- Type-safe route arguments
- Deep link support for notification taps → specific screens
- Lightweight; no need for Voyager or Decompose at this scale

### 6.3 State Management — StateFlow + ViewModel

**Choice:** `StateFlow` exposed from `@HiltViewModel` ViewModels  
**Rationale:**
- Lifecycle-aware by default when collected with `collectAsStateWithLifecycle()`
- Cold flow semantics prevent unnecessary recomposition
- `MutableStateFlow` for simple state, `combine()` for derived state
- No LiveData — StateFlow is the modern, coroutine-native choice

### 6.4 Model Hosting — Foreground Service (Bound)

**Choice:** `LifecycleService` with foreground notification + `Binder` interface  
**Rationale:**
- Model loading takes ~10 seconds and uses ~676 MB GPU memory — must persist across screen rotations and background transitions
- Foreground service prevents Android from killing the process holding the model
- Bound service lets Activities/ViewModels call `process()` directly
- Foreground service type: `specialUse` (with Play Store justification)
- Alternative (ViewModel-hosted model) was rejected: model would be destroyed on config changes, and ViewModel scope is too narrow for a cross-feature resource

### 6.5 Minimum API Level — 31 (Android 12)

**Choice:** `minSdkVersion 31`  
**Rationale:**
- `SpeechRecognizer.createOnDeviceSpeechRecognizer()` requires API 31 — critical for on-device voice privacy
- LiteRT-LM requires Android 12+ per Google's documentation
- Foreground service type enforcement (API 31+) is simpler to handle from the start
- Android 12 covers ~85% of active devices as of April 2026
- Gemma 4 E2B targets "high-end" devices — these overwhelmingly run Android 12+

### 6.6 Target SDK — 35 (Android 15)

**Choice:** `targetSdkVersion 35`  
**Rationale:**
- Play Store requires targeting recent SDK for new app submissions
- Android 15 PendingIntent BAL restrictions are manageable with proper flags
- POST_NOTIFICATIONS runtime permission (API 33+) handled in onboarding

### 6.7 Build System & Key Libraries

| Category | Library | Version Strategy |
|----------|---------|-----------------|
| Build | Gradle Kotlin DSL + Version Catalogs | `libs.versions.toml` |
| DI | Hilt | Latest stable |
| UI | Jetpack Compose (BOM) | Latest BOM |
| Camera | CameraX | Latest stable |
| AI Runtime | LiteRT-LM | `latest.release` from maven.google.com |
| Networking | OkHttp (model download only) | Latest stable |
| Background | WorkManager | Latest stable |
| Serialization | kotlinx.serialization | For JSON parsing of model output |
| Image | Coil (Compose) | For image display in chat |
| Testing | JUnit 5, Turbine, Mockk | Latest stable |

---

## 7. MVP Phasing

### Phase 1: Core Foundation (Weeks 1–3)

**Goal:** Model loads, chat works, basic vision query works.

| Task | Owner | Module |
|------|-------|--------|
| Project scaffold: Hilt, Compose, Navigation | George | `:app`, `:ui` |
| Core data models | Jerry | `:core` |
| Model download manager (HuggingFace → local storage) | Elaine | `:ai` |
| `GemmaService` foreground service + `GemmaEngine` wrapper | Elaine | `:ai` |
| Chat screen (text input → model → response, streaming) | George | `:ui` |
| Onboarding screen (model download progress) | George | `:ui` |
| Basic prompt templates (chat, simple vision) | Elaine | `:ai` |
| Settings screen (model status, clear cache) | George | `:ui` |

**Exit Criteria:** User can download model, open chat, type a question, see streaming response. Can send an image from gallery and get a description.

### Phase 2: Camera + Detection + Captioning (Weeks 4–6)

**Goal:** Live camera preview with object detection overlays, captioning, basic OCR.

| Task | Owner | Module |
|------|-------|--------|
| CameraX integration (preview, frame capture) | George | `:camera` |
| Camera screen with mode selector (Detect / Caption / OCR) | George | `:ui` |
| Detection prompt template + JSON parser | Elaine | `:ai` |
| Bounding box overlay (Canvas composable) | George | `:ui` |
| Coordinate rescaling (1000×1000 → image dims) | Elaine | `:ai` |
| OCR prompt template + text result card | Elaine | `:ai` |
| Image captioning prompt | Elaine | `:ai` |
| Token budget configuration (70–1120 per mode) | Elaine | `:ai` |

**Exit Criteria:** User can point camera, tap detect, see bounding boxes. Can tap OCR and see extracted text. Can tap caption and see description. ~2-3 second latency per detection on GPU.

### Phase 3: Voice Input + Phone Automation (Weeks 7–10)

**Goal:** Voice commands trigger phone actions. Function-calling pipeline works end-to-end.

| Task | Owner | Module |
|------|-------|--------|
| `SpeechRecognizer` wrapper (on-device) | George | `:voice` |
| Voice button in Chat/Camera screens | George | `:ui` |
| TTS feedback wrapper | George | `:voice` |
| Function-calling tool definitions (`PhoneActionToolSet`) | Elaine | `:ai` |
| `FunctionCallParser` | Elaine | `:ai` |
| `ActionDispatcher` + safety validator | Elaine | `:actions` |
| Action handlers: SMS, call, alarm, timer, volume, flashlight | Elaine | `:actions` |
| Permission orchestration (first-run flow) | George | `:app` |
| Confirmation dialog for dangerous actions | George | `:ui` |
| Notification result reporting | George | `:ui` |
| Integration tests for voice → action pipeline | Kramer | All |

**Exit Criteria:** User says "set an alarm for 7 AM" → alarm is set silently. User says "send SMS to Mom saying I'll be late" → confirmation dialog → SMS sent. TTS confirms actions.

### Phase 4: Advanced Features (Weeks 11–14)

**Goal:** AccessibilityService automation, multi-action commands, advanced vision, polish.

| Task | Owner | Module |
|------|-------|--------|
| `AccessibilityService` implementation | Elaine | `:accessibility` |
| UI automation bridge (auto-tap, set-text) | Elaine | `:accessibility` |
| Advanced action handlers: DND, brightness, media, calendar, app launch, navigation | Elaine | `:actions` |
| Multi-action command support (chain function calls) | Elaine | `:ai` |
| VQA mode (multi-turn camera Q&A) | Elaine | `:ai` |
| Continuous voice listening mode (optional foreground service) | George | `:voice` |
| Dark theme, Material 3 polish | George | `:ui` |
| E2E test suite | Kramer | All |
| Performance profiling + battery optimization | Elaine | `:ai` |
| Play Store listing prep | George | `:app` |

**Exit Criteria:** Full feature set working. AccessibilityService enables WhatsApp automation. App passes internal QA. Battery impact measured and documented.

---

## 8. Risk Analysis

### 8.1 Model Size vs Device Storage

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| 2.58 GB model download fails or takes too long | Users abandon onboarding | Medium | Resume-capable download via WorkManager + progress UI. Store in app-specific external storage. Show clear size warning before download. Consider future AICore integration (system-managed model, zero download). |
| Device lacks 3 GB free storage | Model can't be stored | Low (flagship targets) | Check storage pre-download. Show clear requirements in Play Store listing. |

### 8.2 Inference Latency

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| 2-3 second detection latency feels slow | Users expect real-time | High | Set expectations in UI (pulsing animation, "Analyzing..."). Use lower token budget (280) for speed when high accuracy isn't needed. Show streaming text for chat. Consider hybrid approach with lightweight on-device detector for future real-time mode. |
| Model initialization takes 10 seconds | App feels broken on launch | Medium | Foreground service loads model once on first use. Show "Model warming up" with progress. Pre-warm with dummy query. Keep model hot in memory via foreground service. |

### 8.3 Play Store AccessibilityService Restrictions

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Google rejects app for AccessibilityService use | Phase 4 features blocked | High | Phase 4 is additive — app is fully functional without it (Phases 1-3). Frame AccessibilityService as genuine assistive technology. Provide sideload option for power users. Consider Play Store submission without AccessibilityService, with sideload variant having full features. |

### 8.4 Battery Drain

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Foreground service + GPU inference drains battery | Users uninstall | Medium | GPU inference is bursty (fast, then idle) — not continuous drain. Model sits idle between requests (minimal passive drain). Provide battery usage stats in Settings. Implement configurable "sleep after X minutes idle" that releases GPU memory. |
| Continuous voice listening drains battery | Background drain complaints | High | Make continuous listening opt-in, not default. Use SpeechRecognizer's built-in silence detection to minimize active recording. Document battery impact honestly. |

### 8.5 Security — AI-Triggered Actions

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Prompt injection in voice command tricks model into dangerous action | Unauthorized SMS/calls | Low | `SafetyValidator` blocks destructive actions entirely. SMS/call always require user confirmation dialog. No action executes without explicit handler code — model can only call registered tools. Rate-limit action execution (max 5 actions per minute). Log all actions for audit. |
| Model hallucinates a function call | Wrong contact called, wrong message sent | Medium | Confirmation dialog shows exact parameters before execution. TTS reads back the planned action for voice-only mode. All actions are reversible (except sent SMS). |
| Malicious image input triggers unintended behavior | Model outputs unexpected content | Low | Vision pipeline only returns detection results / text — never triggers actions. Action pipeline is strictly separated from vision pipeline. |

### 8.6 Model Accuracy

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Object detection misses objects or returns wrong labels | Poor user experience | Medium | Set expectations — this is a VLM, not YOLO. Open-vocabulary is a strength. Allow users to specify what to detect. Token budget 560 for best accuracy. |
| OCR misreads text | Incorrect data extraction | Medium | Use max token budget (1120) for OCR. Show raw image alongside extracted text for verification. Never auto-act on OCR output. |
| Function-calling misinterprets user intent | Wrong action triggered | Medium | Confirmation dialog is the primary safety net. Show model's interpretation before executing. Allow "cancel" with quick undo. |

---

## Appendix A: Manifest Permissions

```xml
<!-- Runtime permissions (requested during onboarding) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Normal permissions (auto-granted) -->
<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.INTERNET" />  <!-- model download only -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Special permissions (granted via Settings UI during onboarding) -->
<!-- WRITE_SETTINGS: for brightness control -->
<!-- NOTIFICATION_POLICY_ACCESS: for DND toggle -->
<!-- ACCESSIBILITY_SERVICE: for UI automation (Phase 4) -->

<!-- GPU support for LiteRT-LM -->
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

## Appendix B: Gradle Dependencies (Version Catalog)

```toml
# libs.versions.toml
[versions]
agp = "8.8.0"
kotlin = "2.1.0"
composeBom = "2026.04.00"
hilt = "2.52"
camerax = "1.5.0"
litertlm = "+"
workmanager = "2.10.0"
kotlinxSerialization = "1.7.3"
coil = "3.0.0"
okhttp = "4.12.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version = "2.8.0" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# CameraX
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }

# AI
litertlm = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Background
workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }

# Image
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

# Network (model download only)
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

## Appendix C: Key Architecture Constraints

1. **No cloud calls for inference.** All AI runs on-device via LiteRT-LM. Internet is used only for model download.
2. **Single model instance.** Only one `Engine` exists at a time, hosted in `GemmaService`. No duplicate model loading.
3. **Confirmation before side-effects.** Any action that sends data externally (SMS, call, email) requires user confirmation.
4. **Modular independence.** `:ui` never imports `:actions`. `:camera` never imports `:ai`. Communication is through `:core` types and ViewModel interfaces.
5. **No AccessibilityService in MVP.** Phases 1-3 work without it. Phase 4 adds it as an enhancement.
6. **Graceful degradation.** If GPU is unavailable, fall back to CPU. If model isn't downloaded, show onboarding. If permission is denied, skip that feature.

---

*End of architecture document.*
