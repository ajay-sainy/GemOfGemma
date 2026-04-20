# Squad Decisions

## Active Decisions

No decisions recorded yet.

## Governance

- All meaningful changes require team consensus
- Document architectural decisions here
- Keep history focused on work, decisions focused on direction
### 2026-04-18: Project scope expansion — ObjectDetection → GemOfGemma
**By:** Ajay Sainy
**What:** Project renamed from ObjectDetection to GemOfGemma. Scope expanded from object detection only to an all-in-one on-device AI assistant covering: object detection, image captioning, visual Q&A, OCR, on-device chat, voice commands, and full phone automation (SMS, calls, alarms, connectivity toggles, device settings, app launching, navigation). Architecture uses Gemma 4 E2B model with function-calling for phone actions, AccessibilityService for silent execution, SpeechRecognizer for voice input, and foreground service to keep everything alive.
**Why:** Gemma 4's multimodal capabilities and function-calling make it possible to build a single on-device assistant that handles all these features with one model. Each additional capability is just a different prompt.
### 2026-04-18: UI quality directive
**By:** Ajay Sainy (via Copilot)
**What:** The UI must be production-grade. Classy look and feel. Perfect user experience. Not a high school student app. Every screen, animation, spacing, typography, and interaction must feel polished and professional.
**Why:** User request — captured for team memory
# Decision: AI + Actions + Accessibility Module Architecture

**Author:** Elaine (ML Engineer)  
**Date:** April 17, 2026  
**Status:** Implemented (Phase 1)

## Context

Building the `:ai`, `:actions`, and `:accessibility` modules as specified in Jerry's architecture doc (10-architecture.md). Several design choices were needed for how the AI layer interfaces with action execution.

## Decisions

### 1. Manual function-calling over LiteRT-LM native ToolSet

**Choice:** Embed tool definitions in the voice command system prompt as text. Model outputs `<function_call>` JSON tags, parsed by FunctionCallParser.

**Rationale:** The LiteRT-LM ToolSet interface requires synchronous method implementations that get called by the framework. Our action handlers are async (suspend functions) and need safety validation before execution. Prompt-based function calling gives us full control over parsing, validation, and execution order. Can migrate to native ToolSet later if needed.

### 2. ActionResult stays in :core, typealias in :actions

**Choice:** George's `ActionResult` sealed class in `:core` is the single source of truth. The `:actions` module re-exports it via typealias.

**Rationale:** Avoids duplication. All modules already depend on `:core`, so ActionResult is universally accessible.

### 3. GemmaService orchestrates the full voice command flow

**Choice:** GemmaService (in `:ai`) has ActionDispatcher injected and handles the entire voice command pipeline: inference → parse function call → dispatch action → return result.

**Rationale:** Keeps ViewModel thin. The service already runs on a background thread. `:ai` depends on `:actions` per the architecture graph. NeedsConfirmation results bubble up to the UI layer for dialog display.

### 4. Safety validation is a hard gate, not advisory

**Choice:** SafetyValidator blocks execution for dangerous actions (wipeData, install/uninstall) and requires explicit user confirmation for sensitive ones (SMS, calls). No bypass except through `dispatchConfirmed()` after user approval.

**Rationale:** AI-triggered phone actions carry real-world consequences. Defense in depth — even if the model hallucinates a destructive action, the validator blocks it.

## Risks

- LiteRT-LM API surface may differ from research docs. Imports use `com.google.ai.edge.litertlm.*` based on published examples. Comments flag areas that may need adjustment.
- Model download URL (HuggingFace) may change. ModelDownloadManager.MODEL_URL should be verified against the actual distribution.
# Decision: Context Management — Persistent Conversation

**Author:** Elaine (ML Engineer)  
**Date:** 2026-04-18  
**Status:** IMPLEMENTED  
**Priority:** CRITICAL — #1 user complaint

---

## Problem

Chat only sends the last message to Gemma. The AI has no memory of previous messages because `GemmaService.processChat()` creates a new `Conversation` object for every request, destroying all prior history.

## Decision

**Use persistent LiteRT-LM Conversation objects** (Option A), matching the pattern used by Google's official AI Edge Gallery app.

### Why Option A (persistent conversation) over Option B (manual history rebuild)?

1. LiteRT-LM's `Conversation` object already maintains history internally across `sendMessage()` calls — this is its designed purpose
2. Rebuilding via `initialMessages` on every request would re-process all prior tokens (slow, wasteful)
3. Google's reference implementation (Gallery app) uses exactly this pattern
4. Simpler code, fewer moving parts, less room for bugs

## Changes Made

| File | Change |
|------|--------|
| `GemmaEngine.kt` | Manages conversations by ID via `ConcurrentHashMap<String, ManagedConversation>`. New methods: `getOrCreateConversation()`, `resetConversation()`, `closeConversation()` |
| `GemmaService.kt` | `processChat()` reuses persistent conversation. Adds context-reset at ~6K tokens with recent history replay. Recovery from process death via `initialMessages` |
| `AiRequest.kt` | `TextChat.history: List<ChatMessage>` added for recovery/context-reset |
| `ChatViewModel.kt` | Passes `_uiState.value.messages` as history. Added `clearChat()` for explicit reset |
| `AiProcessor.kt` | Added `resetChat(conversationId)` interface method |
| `GemmaServiceConnector.kt` | Implements `resetChat()` delegation |

## Context Budget

- Gemma 4 E2B: 32K token context window
- Auto-reset threshold: 6,000 estimated tokens
- Recent history kept on reset: last 20 messages
- Token estimation: ~4 chars per token (SentencePiece heuristic)

## Risks

1. **Token estimation is approximate** — could under/overcount. Mitigation: conservative 6K threshold (well under 32K limit)
2. **Process death loses conversation** — mitigated by recovery via `initialMessages` from ChatViewModel's message list
3. **Context reset changes model behavior** — user may notice slight discontinuity. Mitigation: keep 20 recent messages for continuity

## Research

Full findings in `Gemma4Research/12-context-management.md`
# Decision: Gemma 4 Model Selection for Android Object Detection

**Author:** Elaine (ML Engineer)
**Date:** April 17, 2026
**Status:** Proposed

## Context

Researched Gemma 4 model family to determine viability for our Android object detection app. Gemma 4 was released April 2, 2026.

## Decision

**Use Gemma 4 E2B as the primary model for on-device object detection**, deployed via LiteRT-LM.

## Key Findings

1. **Gemma 4 supports object detection natively** — outputs bounding boxes as JSON with coordinates normalized to 1000×1000 grid. No fine-tuning required.

2. **E2B is the right size for mobile** — 2.58 GB model file, ~676 MB working memory on GPU, runs at 52 tok/s decode on Samsung S26 Ultra.

3. **LiteRT-LM is the recommended runtime** — production-ready Kotlin API, GPU/NPU support, stable Gradle dependency.

4. **This is NOT real-time detection** — expect ~2-3 seconds per detection query. This is a VLM (vision-language model) approach, not traditional YOLO-style detection. Suitable for photo analysis and periodic scanning, not 30fps video.

5. **Open-vocabulary detection** — unlike YOLO/SSD, Gemma 4 can detect ANY object by name without retraining. This is a significant advantage.

## Risks

- Latency may be too high for real-time UX expectations
- Generative output (JSON text) needs robust parsing
- Model download (2.58 GB) requires good UX for first-launch experience
- High-end devices only (Android 12+, 6GB+ RAM)

## Alternatives Considered

- **Gemma 4 E4B**: More capable but 3.65 GB, tighter on mid-range devices
- **Traditional detector (YOLO/SSD)**: Real-time but fixed class set, no reasoning
- **Hybrid approach**: Traditional detector + Gemma 4 for rich analysis — viable evolution path

## Recommendation

Start with Gemma 4 E2B as a single-model solution for the POC. If latency is unacceptable for the intended UX, evolve to a hybrid pipeline later.
# Decision: Migrate from LiteRT-LM Stubs to Real SDK

**Author:** Elaine (ML Engineer)  
**Date:** 2026-04-18  
**Status:** IMPLEMENTED

## Context

We had been developing against hand-written stub classes in `ai/src/main/java/com/google/ai/edge/litertlm/` that mimicked the LiteRT-LM API. The real SDK `com.google.ai.edge.litertlm:litertlm-android:0.10.2` was confirmed on Google Maven on April 17, 2026.

## Decision

Swap in the real Maven dependency and delete all 8 stub files. Adjust calling code for the one breaking API change (`sendMessage()` returns `Message` instead of `Contents`).

## Changes Made

1. **gradle/libs.versions.toml** — Version `0.10.2`, coordinates `com.google.ai.edge.litertlm:litertlm-android`. Removed `litert-lm-gpu` (GPU is bundled).
2. **ai/build.gradle.kts** — Uncommented `implementation(libs.litert.lm)`, removed GPU line.
3. **Deleted 8 stub files** — Backend.kt, Engine.kt, EngineConfig.kt, Conversation.kt, ConversationConfig.kt, Content.kt, Contents.kt, SamplerConfig.kt.
4. **GemmaEngine.kt** — Added model file existence check, nested GPU/CPU fallback with full error capture, exposed `initError` field.
5. **GemmaService.kt** — Uses `Message.text` (real return type) instead of `result.toString()`.
6. **AndroidManifest.xml** — Added `libvndksupport.so` for GPU backend.

## What Was NOT Changed

- **PhoneActionToolSet.kt** — Kept manual prompt-based function calling. The real SDK has `@Tool`/`@ToolParam` annotations and `ToolSet` API, but our prompt-engineering approach is more debuggable and doesn't lock us into the SDK's tool schema. Can migrate later if native tool calling proves more reliable.
- **DetectionResponseParser.kt / FunctionCallParser.kt** — No changes needed; they parse text output which is now accessed via `Message.text`.
- **Streaming** — `sendMessageAsync()` (returns `Flow<Message>`) is available in the real SDK but not wired to UI yet. Future work when streaming UI is built.

## Risks

- Build will fail until Gradle sync pulls the real artifact. Requires `google()` in repository block (already present in `settings.gradle.kts`).
- Runtime will crash if model `.litertlm` file is not on device. GemmaEngine now checks file existence and reports `initError` instead of crashing.
- GPU backend may not be available on all target devices; fallback to CPU is maintained.
### Date: April 18, 2026
**By:** Elaine (ML Engineer)
**What:** Research on fully autonomous (silent) tool capabilities for Gemma 4.
**Why:** The user requested additional possibilities for Android tools that require zero user action/confirmation.

Beyond the baseline APIs (like SMS, calls, or UI automation), Gemma 4 can leverage the following fully autonomous capabilities:

1. **Hardware & Environment Telemetry (Context-Awareness)**
   - `get_ambient_light_level`: Access the light sensor completely silently to determine if the user is in a dark room.
   - `get_motion_state`: Read pedometer/accelerometer silently to know if the user is walking, running, or stationary.
   - `get_battery_diagnostics`: Check exact battery percentage, temperature, and charging state (`BatteryManager`).
   - `get_device_storage_memory`: Silently evaluate available RAM and free disk space (`ActivityManager`, `StatFs`).

2. **Network & Connectivity Context**
   - `get_network_info`: Read WiFi SSID, signal strength (RSSI), and cellular status (LTE/5G) silently (`ConnectivityManager`).
   - `verify_connectivity`: Ping a server or resolve DNS silently to check for captive portals or offline states.

3. **Background Audio & Haptics**
   - `measure_ambient_noise`: Silently sample the microphone for 1-2 seconds (if `RECORD_AUDIO` is granted) to compute environmental decibel levels.
   - `play_haptic_pattern`: Dispatch custom vibration patterns (e.g., Morse code) via `Vibrator` with no UI.
   - `speak_tts`: Use `TextToSpeech` to vocalize answers or alerts without waking the screen.

4. **Data & Compute Utilities (Pure On-Device Functions)**
   - `evaluate_math`: Fast, silent local computation.
   - `read_write_clipboard`: Move data in and out of the clipboard silently (`ClipboardManager`).
   - `convert_timezones`: Local epoch/date formatting.

5. **System Settings Adjustments (Post-Setup)**
   - `set_stream_volume`: Modify media, alarm, or ringer volume streams silently (`AudioManager`).
   - `set_screen_brightness`: Dynamically adjust brightness based on AI logic (requires one-time `WRITE_SETTINGS`).
   - `toggle_flashlight`: Turn the camera torch on/off silently (`CameraManager`).

**Decision:** We will expand the `PhoneActionToolSet` to include environment telemetry (battery, light, network) and compute utilities first, as they require standard or zero permissions and heavily enrich Gemma's context without any user friction.
# Decision: End-to-End AI Pipeline Wiring

**Author:** Elaine (ML Engineer)
**Date:** April 18, 2026
**Status:** Implemented

## Context

The app built and ran, but all AI, voice, and camera capabilities were disconnected. ViewModels had TODO placeholders, AppModule was empty, and no service binding existed. The GemmaService, GemmaEngine, ActionDispatcher, and all handlers were fully functional but unreachable from the UI layer.

## Decision

### DI Architecture: Interface-in-Core Pattern

Created `AiProcessor` interface in `:core` with a single method `suspend fun process(AiRequest): AiResponse`. ViewModels in `:ui` inject this interface — they never see `GemmaService`, `GemmaEngine`, or any `:ai` internals. The concrete binding (`GemmaServiceConnector → AiProcessor`) is defined in `:ai`'s `AiBindingModule` and resolved at the `:app` component level.

**Why not add `:ai` as a dependency of `:ui`?** While it wouldn't create a circular dep today, it couples the UI to the service implementation. If we swap to AICore, a server-based backend, or mock for testing, we only change the binding — not every ViewModel.

### Service Binding: MutableStateFlow Connector

`GemmaServiceConnector` is a `@Singleton` that starts and binds to `GemmaService` in its `init {}` block. It uses `MutableStateFlow<GemmaService?>` to track the bound service. The `process()` method awaits the first non-null emission with a 15-second timeout, returning a friendly error if the service isn't ready.

**Why not CompletableDeferred?** It only completes once. If the service disconnects and reconnects (process death, crash recovery), `MutableStateFlow` naturally handles the new binding.

### Voice and Camera: Direct Dependencies

Added `:voice` and `:camera` as dependencies of `:ui` rather than creating additional interfaces. Both modules only depend on `:core`, so no circular deps. Creating `VoiceInput`/`ImageCaptureSource` abstractions would be over-engineering — these are thin Android wrappers unlikely to have multiple implementations.

## Files Changed

| File | Change |
|------|--------|
| `core/.../AiProcessor.kt` | Created — interface |
| `ai/.../GemmaServiceConnector.kt` | Created — ServiceConnection + AiProcessor impl |
| `ai/.../di/AiModule.kt` | Added AiBindingModule with @Binds |
| `app/.../di/AppModule.kt` | Cleaned up TODOs |
| `app/.../MainActivity.kt` | Added startForegroundService for eager model loading |
| `ui/.../chat/ChatViewModel.kt` | Full rewrite — AiProcessor + VoiceRecognizer wiring |
| `ui/.../camera/CameraViewModel.kt` | Full rewrite — AiProcessor + CameraManager wiring |
| `ui/build.gradle.kts` | Added :voice and :camera dependencies |

## Risks

- **15s timeout** may not be enough on slow devices downloading 2.58GB model for the first time. The model download happens in `GemmaService.onCreate()` — first `process()` call will timeout and show error. User retries after download completes.
- **Foreground service start** in `MainActivity.onCreate()` requires `FOREGROUND_SERVICE` permission in manifest (should already be declared for GemmaService).
- **Voice permission** (`RECORD_AUDIO`) must be requested before `toggleRecording()`. Not handled here — assumed to be handled in the UI composable.
# Decision: Phase 1 Project Scaffold

**Author:** George  
**Date:** April 17, 2026  
**Status:** Implemented

## What

Scaffolded the full Android project structure for GemOfGemma with 5 modules (`:app`, `:core`, `:ui`, `:camera`, `:voice`), Gradle Kotlin DSL, version catalogs, Hilt DI, Compose Navigation, and CameraX integration.

## Key Decisions Made

1. **minSdk = 31** — Followed architecture doc (Jerry). Required for `SpeechRecognizer.createOnDeviceSpeechRecognizer()` and LiteRT-LM. Task spec said 26 but architecture rationale was explicit.

2. **Java 17 target** — All modules use JVM 17 for compatibility with latest AGP and Kotlin compiler.

3. **Dynamic color enabled by default** — Theme falls back to custom Gem palette on non-Material You devices but uses dynamic color on Android 12+ (which is all devices given minSdk 31).

4. **GemmaService/AccessibilityService declarations commented out in manifest** — These belong to Elaine's modules. Manifest is ready to uncomment when `:ai` and `:accessibility` are built.

5. **ChatViewModel has placeholder echo response** — Sends back received text until GemmaService is wired in. Lets us test UI flow immediately.

## Integration Points for Elaine

- `ChatViewModel` — needs `GemmaService` binder injection
- `CameraViewModel.onImageCaptured()` — needs to route `AiRequest.VisionQuery` to GemmaService
- `OnboardingScreen` step 2 — needs `ModelDownloadManager` for download progress
- `SettingsScreen` — needs `ModelStatus` observable from GemmaService
- `AppModule` — needs GemmaService and ActionDispatcher bindings
- `AndroidManifest.xml` — needs service declarations uncommented
# Decision: Production UI Overhaul

**Author:** George (Android Dev)  
**Date:** April 17, 2026  
**Status:** Implemented

## Context

The existing UI was scaffold-grade: basic `Scaffold` + `TopAppBar` + `Card` with default Material 3 colors and no custom typography, animations, or design system. User directive was explicit: "production grade, not a high school student created app."

## Decision

Complete rewrite of every UI screen, theme system, and navigation architecture to premium quality.

### Key Design Decisions

1. **Plus Jakarta Sans** via Google Fonts provider — modern, geometric, premium feel. Runtime-loaded so no APK size increase from bundled fonts.

2. **Deep indigo/violet palette** instead of Google blue — creates a distinct AI-assistant brand identity. Warm gradient accents (violet→blue→fuschia) for CTAs and gem icon.

3. **Bottom NavigationBar** replacing top-bar icon navigation — standard pattern for primary destinations. Chat, Camera, Settings as equal-weight tabs.

4. **Custom component library** (5 components) — ensures visual consistency. AnimatedGemIcon serves as the AI avatar/brand mark throughout the app.

5. **Edge-to-edge camera** — full immersive experience with floating controls instead of toolbar-based layout. BottomSheetScaffold for results.

6. **HorizontalPager onboarding** — smooth, swipeable 4-page experience with animated page indicators instead of manual step counter.

7. **No dynamic color / Material You** — brand consistency across all devices. Custom color scheme always applies.

## Files Changed

### Created
- `ui/src/main/java/com/gemofgemma/ui/theme/Type.kt`
- `ui/src/main/java/com/gemofgemma/ui/theme/Shape.kt`
- `ui/src/main/java/com/gemofgemma/ui/components/AnimatedGemIcon.kt`
- `ui/src/main/java/com/gemofgemma/ui/components/GradientButton.kt`
- `ui/src/main/java/com/gemofgemma/ui/components/GlassmorphismCard.kt`
- `ui/src/main/java/com/gemofgemma/ui/components/ThinkingIndicator.kt`
- `ui/src/main/java/com/gemofgemma/ui/components/FeatureChip.kt`
- `ui/src/main/res/values/font_certs.xml`

### Updated
- `ui/src/main/java/com/gemofgemma/ui/theme/Color.kt` — new palette
- `ui/src/main/java/com/gemofgemma/ui/theme/Theme.kt` — typography, shapes, edge-to-edge
- `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` — complete rewrite
- `ui/src/main/java/com/gemofgemma/ui/chat/ChatViewModel.kt` — added toggleRecording
- `ui/src/main/java/com/gemofgemma/ui/chat/ChatUiState.kt` — added isRecording
- `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` — complete rewrite
- `ui/src/main/java/com/gemofgemma/ui/camera/CameraUiState.kt` — added VQA mode
- `ui/src/main/java/com/gemofgemma/ui/camera/CameraViewModel.kt` — VQA support
- `ui/src/main/java/com/gemofgemma/ui/camera/BoundingBoxOverlay.kt` — animations, category colors
- `ui/src/main/java/com/gemofgemma/ui/onboarding/OnboardingScreen.kt` — HorizontalPager rewrite
- `ui/src/main/java/com/gemofgemma/ui/settings/SettingsScreen.kt` — ListItem-based rewrite
- `app/src/main/java/com/gemofgemma/navigation/NavGraph.kt` — bottom nav
- `gradle/libs.versions.toml` — new deps
- `ui/build.gradle.kts` — new deps

## Impact on Other Modules

- `ChatScreen` no longer takes `onNavigateToCamera`/`onNavigateToSettings` params (bottom nav handles navigation)
- `SettingsScreen` no longer takes `onNavigateBack` param (bottom nav tab)
- AI/voice integration points remain as TODOs — no breaking changes to `:ai`, `:voice`, `:actions` modules
# Decision: Vision UX Overhaul

**Author:** George (Android Dev)  
**Date:** April 18, 2026  
**Triggered by:** Ajay Sainy — "All Vision mini apps are shit. They work, but the user experience is totally bad."  
**Related:** Jerry's UX audit (jerry-ux-audit.md) — P0-5, P0-7, P0-8, P1-6, P1-11, P1-13, P2-7, P2-12

---

## Summary

Complete overhaul of the Camera/Vision screen across all 4 vision modes (Object Detection, Image Captioning, OCR, Visual Q&A). Transformed from a basic prototype with shared UI across all modes into a polished, Google Lens–quality experience where each mode feels like its own mini-app.

## Changes Made

### Files Modified
| File | Action | Lines |
|---|---|---|
| `core/.../DetectionResult.kt` | Added `confidence` field | ~5 |
| `ui/.../theme/Color.kt` | Added mode accent + detection colors | ~10 |
| `ui/.../camera/CameraUiState.kt` | **Rewritten** — rich state with VQA, history, per-mode data | ~105 |
| `ui/.../camera/CameraViewModel.kt` | **Rewritten** — VQA logic, history, shutter animation, per-mode processing | ~225 |
| `ui/.../camera/BoundingBoxOverlay.kt` | **Rewritten** — category colors, staggered springs, corner brackets, tap selection | ~190 |
| `ui/.../camera/CameraScreen.kt` | **Rewritten** — all new composables for each mode, mode selector, shutter, history | ~750 |

### Jerry's Audit Items Addressed
- **P0-5** (hardcoded 1000×1000): `imageWidth`/`imageHeight` now stored in state (though CameraManager still needs to populate actual dimensions — flagged for follow-up)
- **P0-7** (shutter no-op animation): Fixed with `MutableInteractionSource` + `collectIsPressedAsState()` — real scale animation on press
- **P0-8** (Copy/Share TODOs): Implemented with `ClipboardManager` and `Intent.ACTION_SEND`
- **P1-6** (VQA no text input): Full VQA panel with text input, conversation history, follow-up questions
- **P1-11** (controls overlap sheet): Removed BottomSheetScaffold entirely, using frosted overlay cards with proper spacing
- **P1-13** (no haptics): Added `HapticFeedbackType.LongPress` on shutter press and VQA send
- **P2-7** (no animated mode selector): Sliding indicator pill with spring animation, icons + labels
- **P2-12** (plain detection list): Color-coded chips with confidence percentage, matching bounding box colors

### Architecture Decisions
1. **No BottomSheetScaffold** — Replaced with custom `FrostedBottomCard` composable overlaid on camera. Gives full control over positioning and avoids the fragile hardcoded-padding-to-avoid-sheet problem
2. **Per-mode result panels** — Each mode has its own result composable rather than a shared `ResultsSheet`. This allows mode-specific features (typewriter for captions, selectable text for OCR, chat for VQA)
3. **Result history in ViewModel state** — Kept simple with `List<VisionResult>` capped at 10 items rather than a Room database. Sufficient for in-session history; persistent history would need a future migration
4. **Category color mapping via keyword matching** — Pragmatic approach using keyword sets rather than a taxonomy database. Covers ~40 common object labels. Falls back to purple for unknowns

## Open Items
- [ ] `CameraManager.captureImage()` should return image dimensions alongside bytes so `imageWidth`/`imageHeight` reflect actual sensor output
- [ ] Continuous real-time detection mode (stream frames to model without manual capture) — needs investigation on Gemma 4's frame processing throughput
- [ ] Gallery picker for VQA mode (pick existing photo instead of camera capture)
- [ ] Result history persistence across sessions (Room or DataStore)

## Risk Assessment
- **Low risk:** All changes are contained within `:ui` and one backward-compatible addition to `:core`. No changes to `:ai`, `:camera`, `:actions`, or `:accessibility` modules
- **DetectionResult.confidence** has a default value of `0f`, so all existing serialization and construction is unaffected
# Architecture Decisions — Jerry (Lead)

**Date:** April 17, 2026  
**Context:** Initial architecture design for GemOfGemma based on Elaine's Gemma 4 research (docs 00–09).

---

## Decision 1: Gemma 4 E2B as Sole On-Device Model

**Status:** Accepted  
**Choice:** Use Gemma 4 E2B (2.58 GB, 2.3B effective params) for all AI tasks — vision, NLU, function-calling, chat.  
**Alternatives considered:** E4B (3.65 GB, more accurate but tighter on mid-range RAM), hybrid E2B+YOLO (better real-time detection but double complexity).  
**Rationale:** E2B fits on modern phones, runs at 52 tok/s GPU, handles all modalities in a single model. One model = one engine = simpler architecture. Hybrid can be added later if real-time detection is needed.

## Decision 2: LiteRT-LM as Inference Runtime

**Status:** Accepted  
**Choice:** LiteRT-LM (Google AI Edge) — `com.google.ai.edge.litertlm:litertlm-android`.  
**Alternatives considered:** MediaPipe LLM (deprecated), AICore (developer preview, not GA).  
**Rationale:** Production-ready, officially recommended by Google, supports GPU/CPU/NPU backends, native Kotlin API with streaming and tool-calling support. MediaPipe is deprecated. AICore is promising but not stable enough.

## Decision 3: Foreground Service for Model Hosting

**Status:** Accepted  
**Choice:** `LifecycleService` with `foregroundServiceType="specialUse"` + bound service binder.  
**Alternatives considered:** ViewModel-scoped model (destroyed on config changes), Application-scoped singleton (no lifecycle management).  
**Rationale:** 10-second init + 676 MB GPU memory demand a persistent host. Foreground service survives activity recreation, prevents process kill, and provides clean lifecycle.

## Decision 4: Hilt for Dependency Injection

**Status:** Accepted  
**Choice:** Hilt (Dagger-based, compile-time).  
**Alternatives considered:** Koin (runtime, simpler setup).  
**Rationale:** 8 modules with complex cross-module dependencies. Compile-time validation catches wiring errors at build. `@HiltViewModel` + `@AndroidEntryPoint` for Services is seamless.

## Decision 5: minSdkVersion 31 (Android 12)

**Status:** Accepted  
**Choice:** API 31 minimum.  
**Alternatives considered:** API 26 (broader reach), API 33 (simpler notification permissions).  
**Rationale:** `SpeechRecognizer.createOnDeviceSpeechRecognizer()` requires API 31 — essential for voice privacy. LiteRT-LM requires Android 12+. Target devices (flagships with 8+ GB RAM) overwhelmingly run 12+.

## Decision 6: AccessibilityService Deferred to Phase 4

**Status:** Accepted  
**Choice:** Ship Phases 1-3 without AccessibilityService. Add in Phase 4 as enhancement.  
**Alternatives considered:** Include from Phase 1 (highest capability), skip entirely (lower risk).  
**Rationale:** High Play Store rejection risk. App must be fully functional without it. Provides incremental value (WhatsApp automation, WiFi/BT toggles) but is not core. Sideload variant can include it for power users.

## Decision 7: Confirmation Required for External-Facing Actions

**Status:** Accepted  
**Choice:** SMS, phone calls, and any action sending data externally MUST show a confirmation dialog before executing.  
**Alternatives considered:** Trust model output fully (dangerous), block all actions (too restrictive).  
**Rationale:** AI hallucination risk + prompt injection risk. Wrong contact or wrong message could have real-world consequences. Safety over speed.

## Decision 8: Module Ownership Split

**Status:** Accepted  
**Choice:** George owns `:ui`, `:camera`, `:voice` (user-facing). Elaine owns `:ai`, `:actions`, `:accessibility` (system-facing).  
**Rationale:** Clean boundary at the ViewModel interface. George never touches model internals. Elaine never touches Compose. Both can work in parallel without merge conflicts.

---

*These decisions should be respected by all team members. Challenge via PR comments, not unilateral changes.*
# UX Audit — GemOfGemma

**Author:** Jerry (Lead)  
**Date:** April 18, 2026  
**Requested by:** Ajay Sainy  
**Directive:** "What we implemented is MVP like prototype. User experience is shit. Revisit and come up with findings."

---

## Executive Summary

The app has solid *structure* — screens exist, navigation works, theming is applied, onboarding flow is thoughtful. But Ajay is right: it feels like a hackathon demo wearing a designer costume. The UI code is ~70% skeleton with good bones, but the remaining 30% of craft — error handling, feedback loops, micro-interactions, functional completeness — is what separates "runs" from "ships." Below are 38 specific findings, prioritized ruthlessly.

---

## P0 — Critical (Makes the app feel amateur. Fix immediately.)

### P0-1: Error state in Chat is invisible
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt`  
**Issue:** `ChatUiState.error` is populated by the ViewModel on failure, but **ChatScreen never renders it**. The error string is set and forgotten. If the model fails, the user sees the thinking indicator disappear and nothing else — a silent failure.  
**Fix:** Add an animated error banner (similar to the existing "AI is offline" banner) or render the error as a system message in the chat list. Show a retry button.

### P0-2: Suggestion chips display literal quote marks
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` ~L174-180  
**Issue:** Suggestions are defined as `"\"What's the weather like?\""`. The quotes are removed when sent via `removeSurrounding("\"")`, but the **chip label** still shows `"What's the weather like?"` with typographic quotes. On-device screenshot confirms this. Looks like a bug, not a design choice.  
**Fix:** Store suggestions without wrapping quotes. If you want visual quotes, use styled text or a prefix icon, not string literals.

### P0-3: Settings permissions are all hardcoded `false`
**File:** `ui/src/main/java/com/gemofgemma/ui/settings/SettingsScreen.kt` ~L208-217  
**Issue:** Every `PermissionStatusItem` passes `granted = false`. The screen never checks actual permission state via `ContextCompat.checkSelfPermission()`. User sees "Not granted" for Camera even after granting it. This erodes trust — the user just gave permission and the app says otherwise.  
**Fix:** Inject or check actual permission state. Use `rememberMultiplePermissionsState` from Accompanist or manual checks.

### P0-4: Dark Mode toggle is cosmetic — does nothing
**File:** `ui/src/main/java/com/gemofgemma/ui/settings/SettingsScreen.kt` ~L236-237  
**Issue:** `var darkMode by remember { mutableStateOf(false) }` — this is local Compose state. Toggling it doesn't propagate to `GemOfGemmaTheme`, which reads `isSystemInDarkTheme()`. The toggle lies.  
**Fix:** Either remove the toggle and say "Follows system" (honest), or implement a `ThemeRepository` with DataStore that feeds into `GemOfGemmaTheme`.

### P0-5: Camera bounding box uses hardcoded 1000×1000 dimensions
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` ~L178  
**Issue:** `BoundingBoxOverlay(detections = ..., imageWidth = 1000, imageHeight = 1000)`. The actual captured image is whatever the camera sensor produces (e.g., 4000×3000). Bounding boxes will be wildly misaligned.  
**Fix:** Store actual image dimensions from `CameraManager.captureImage()` in `CameraUiState` and pass them through.

### P0-6: No message copy/selection in Chat
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` — `ChatBubble`  
**Issue:** AI responses cannot be copied. No `SelectionContainer`, no long-press menu, no copy button. This is table-stakes for any chat app — users need to copy AI-generated text.  
**Fix:** Wrap the message `Text` in `SelectionContainer {}` or add a copy icon button on long-press. Both ChatGPT and Gemini have this.

### P0-7: ShutterButton has no press animation — the scale animation is a no-op
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` ~L284-295  
**Issue:** `animateFloatAsState(targetValue = 1f, ...)` — the target never changes. There's no `InteractionSource` tracking, no `pressed` state. The animation code exists but literally does nothing. Combined with `Modifier.clickable` which only gives a ripple, the shutter button feels dead — no press-down scale, no release bounce. On a camera, the shutter moment should feel *satisfying*.  
**Fix:** Use `MutableInteractionSource`, detect `isPressed`, animate scale to `0.9f` on press and `1f` on release with a bouncy spring.

### P0-8: Camera results Copy and Share buttons are TODO stubs
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` — `ResultCard` ~L387-395  
**Issue:** `onClick = { /* TODO: Copy to clipboard */ }` and `onClick = { /* TODO: Share */ }`. These icons are visible to the user but do nothing when tapped. This is worse than not showing them at all.  
**Fix:** Implement `ClipboardManager.setPrimaryClip()` for copy and `Intent.ACTION_SEND` for share, or hide the icons until functional.

---

## P1 — Important (Noticeably improves quality. Fix soon.)

### P1-1: No markdown/rich text rendering in AI responses
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` — `ChatBubble`  
**Issue:** AI responses render as plain `Text()`. Gemma 4 returns markdown (bold, code, lists). A response like `**Important:** Use \`adb\` command` shows raw asterisks and backticks. This is the #1 thing that screams "prototype."  
**Fix:** Use a Markdown renderer library (e.g., `compose-markdown` or `Markwon` via `AndroidView`). At minimum, handle bold, code blocks, and bullet lists.

### P1-2: No timestamps on messages
**File:** `ui/src/main/java/com/gemofgemma/core/model/ChatMessage.kt` / `ChatBubble`  
**Issue:** Messages have no visible timestamp. The data class likely lacks a `timestamp` field. Every chat app shows at least relative time ("2m ago", "3:45 PM"). Without it, conversations feel like static mockups.  
**Fix:** Add `val timestamp: Long = System.currentTimeMillis()` to `ChatMessage`, render it below the bubble in `labelSmall` with 0.5f alpha.

### P1-3: No "scroll to bottom" FAB in chat
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt`  
**Issue:** When user scrolls up through history and a new AI response arrives, `animateScrollToItem` jumps them to the bottom. But if they were deliberately reading history, this is jarring. There's no "↓" FAB to manually jump. Standard in Telegram, WhatsApp, ChatGPT.  
**Fix:** Detect `listState.isScrolledToEnd`, show a floating "scroll to bottom" button when scrolled up, and suppress auto-scroll when user is reading history.

### P1-4: No "New Chat" / "Clear Conversation" action
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt`  
**Issue:** The user is locked into a single conversation forever (persisted by `conversationId` in ViewModel). No way to start fresh. ChatGPT/Gemini have prominent "New Chat" buttons.  
**Fix:** Add an icon button in the top bar (or a menu) to clear messages and generate a new `conversationId`.

### P1-5: Settings screen has no title/top bar
**File:** `ui/src/main/java/com/gemofgemma/ui/settings/SettingsScreen.kt`  
**Issue:** The screen jumps straight to "Model" section header. Looking at the screenshot, there's no "Settings" title at the top, unlike Chat which has "GemOfGemma" header. This breaks consistency.  
**Fix:** Add a top bar with "Settings" title, consistent with the Chat screen's top bar treatment.

### P1-6: VQA camera mode doesn't accept user questions
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraViewModel.kt` ~L65  
**Issue:** VQA mode hardcodes `"What is shown in this image?"`. The whole point of Visual Q&A is the user asks a specific question about what they see ("What brand is this?", "Is this ripe?"). There's no text input.  
**Fix:** Add an optional text field in the bottom controls when VQA mode is selected. Pass user's question to `AiRequest.VisionQuery`.

### P1-7: Navigation has zero enter/exit transitions
**File:** `app/src/main/java/com/gemofgemma/navigation/NavGraph.kt` ~L131-158  
**Issue:** `NavHost` composable destinations have no `enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition`. Switching between Chat → Camera → Settings is an instant cut. Feels like web page navigation, not a native app.  
**Fix:** Add `fadeIn() + slideInHorizontally()` / `fadeOut() + slideOutHorizontally()` transitions. Or use `fadeThrough` from `material-motion-compose` for M3 consistency.

### P1-8: User message color has poor contrast in light mode
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` — `ChatBubble` & `ui/theme/Color.kt`  
**Issue:** User bubble uses `MaterialTheme.colorScheme.primary` (#6200EE light / #D0BCFF dark) with `onPrimary` text. In dark mode screenshot, the user bubble is **lavender** (#D0BCFF) with dark text — it's readable but feels washed out and generic. The color doesn't create the clear visual distinction that ChatGPT (green/dark) or iMessage (blue/white) achieves.  
**Fix:** Use a more saturated, opaque user bubble color. Consider a gradient brush for user bubbles instead of flat primary.

### P1-9: Accessibility Service toggle is a fake switch
**File:** `ui/src/main/java/com/gemofgemma/ui/settings/SettingsScreen.kt` ~L247-252  
**Issue:** `var a11yEnabled by remember { mutableStateOf(false) }` with `// TODO: Open system Accessibility settings`. Same pattern as the dark mode toggle — lies to the user. An `AccessibilityService` can't be enabled via a switch; it requires system Settings navigation.  
**Fix:** Replace the switch with a "Configure →" button that opens `Settings.ACTION_ACCESSIBILITY_SETTINGS` via intent. Show actual service state by checking `AccessibilityServiceInfo`.

### P1-10: Raw exception messages shown to user on error
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatViewModel.kt` ~L108  
**Issue:** `error = "Failed to get response: ${e.message}"` — this could show Java stack trace fragments or technical errors like "DEADLINE_EXCEEDED" or "java.net.UnknownHostException". Users don't speak Java.  
**Fix:** Map known exceptions to friendly messages. Generic fallback: "Something went wrong. Please try again."

### P1-11: Camera screen bottom controls overlap with results sheet
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` ~L198  
**Issue:** `padding(bottom = if (hasResults) 128.dp else 24.dp)` — this is a hardcoded offset to avoid the bottom sheet. If the sheet content is taller or shorter, controls either overlap or float too high. This is fragile.  
**Fix:** Use the sheet's actual measured height or place controls within the scaffold's content area above the sheet.

### P1-12: `GlassmorphismCard` component is unused dead code
**File:** `ui/src/main/java/com/gemofgemma/ui/components/GlassmorphismCard.kt`  
**Issue:** This component is defined but never referenced anywhere in the codebase. It also doesn't actually apply blur to content behind it — `Modifier.blur()` blurs the card's own background, not what's underneath (that's not glassmorphism).  
**Fix:** Delete it, or actually use it for camera overlay cards. If keeping, implement real glassmorphism with `RenderEffect.createBlurEffect` on the background.

### P1-13: No haptic feedback on key interactions
**Files:** `ChatScreen.kt` (send), `CameraScreen.kt` (shutter), `OnboardingScreen.kt` (buttons)  
**Issue:** Sending a message, pressing the shutter, granting permissions — none trigger haptic feedback. Premium apps use `HapticFeedbackType.LongPress` or `performHapticFeedback(CONTEXT_CLICK)` for these moments.  
**Fix:** Add `LocalHapticFeedback.current.performHapticFeedback()` on send, capture, and key button presses.

---

## P2 — Nice to Have (Distinguishes good from great.)

### P2-1: Animated gem icon spins continuously, even when user is reading
**File:** `ui/src/main/java/com/gemofgemma/ui/components/AnimatedGemIcon.kt`  
**Issue:** The gem rotates at 8s per full revolution forever (8000ms tween). In the chat empty state, this is a large 72dp spinning hexagon above the "Ask me anything" text. It's attention-grabbing initially but becomes distracting and drains battery (continuous recomposition).  
**Fix:** Animate only on first appearance (2-3 rotations), then settle. Or only spin when processing/loading.

### P2-2: Plus Jakarta Sans font depends on Google Play Services
**File:** `ui/src/main/java/com/gemofgemma/ui/theme/Type.kt`  
**Issue:** Font is loaded via `GoogleFont.Provider` which requires GMS. On devices without GMS (Huawei, some Chinese OEMs), the font silently falls back to system sans-serif with no graceful handling. The app looks like a different app.  
**Fix:** Bundle the font as a local asset (`res/font/`) as primary, keep Google Fonts as optimization. Or at minimum add a fallback `FontFamily`.

### P2-3: No "typing…" indicator with assistant avatar context
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` ~L248-256  
**Issue:** The thinking indicator is just 3 bouncing dots next to a gem icon. It appears inline but lacks personality. ChatGPT shows "ChatGPT is thinking…" text. Gemini shows a shimmer effect. Ours is functional but flat.  
**Fix:** Add a text label "Thinking…" next to the dots. Consider a shimmer or pulsing glow effect on the gem.

### P2-4: Onboarding pages lack enter animations
**File:** `ui/src/main/java/com/gemofgemma/ui/onboarding/OnboardingScreen.kt`  
**Issue:** `HorizontalPager` handles the slide transition between pages, but individual page content doesn't animate. Feature cards don't stagger in, the gem icon doesn't scale up, text doesn't fade. First impression is "slides" not "experience."  
**Fix:** Add `AnimatedVisibility` or `LaunchedEffect`-driven animations for content elements when the page becomes visible.

### P2-5: Chat input field doesn't grow smoothly with multiline input
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` — `ChatInputBar`  
**Issue:** `maxLines = 5` is set but the field height change isn't animated. As the user types multiple lines, the field snaps to a new height. Telegram and iMessage animate this smoothly.  
**Fix:** Wrap the TextField in a container with `animateContentSize()`.

### P2-6: No empty state for camera results sheet
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` — `ResultsSheet`  
**Issue:** When `sheetPeekHeight = 0.dp` (no results), the sheet is invisible. But if the user manually drags up, they see an empty sheet with just the handle. Should show "Take a photo to see results" or similar.  
**Fix:** Add an empty state message inside `ResultsSheet` when no results are present.

### P2-7: Camera mode selector has no animated selection indicator
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` — `ModeSelectorPill`  
**Issue:** Mode selection changes background color instantly (`Color.White.copy(alpha = 0.2f)` vs `Color.Transparent`). No sliding indicator, no cross-fade. Compare with Instagram's camera mode selector which has a smooth sliding pill.  
**Fix:** Use `animateColorAsState` at minimum, or a sliding indicator with `Modifier.offset` animated via `animateDpAsState`.

### P2-8: Version hardcoded as "1.0.0"
**File:** `ui/src/main/java/com/gemofgemma/ui/settings/SettingsScreen.kt` ~L277  
**Issue:** `Text("1.0.0")` — should read from `BuildConfig.VERSION_NAME` for accuracy.  
**Fix:** Pass version string from app module or read via package manager.

### P2-9: No camera flash or lens toggle
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt`  
**Issue:** No flash toggle button, no front/rear camera switch. These are standard camera controls.  
**Fix:** Add icon buttons in the top bar of camera screen.

### P2-10: Bottom sheet drag handle has no semantic meaning
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` ~L106  
**Issue:** The drag handle `Box` has no `contentDescription`. Screen readers can't identify it.  
**Fix:** Add `semantics { contentDescription = "Drag to expand results" }`.

### P2-11: No streaming/typewriter effect for AI responses
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` ~L222  
**Issue:** `uiState.streamingText` is supported in the UI, but `ChatViewModel` doesn't stream — it waits for the full `AiResponse` and adds it all at once. The streaming bubble code exists but is never triggered. The response appears as a block, not word-by-word.  
**Fix:** If LiteRT-LM supports token-by-token callbacks, pipe them through `streamingText` for a ChatGPT-like typewriter feel.

### P2-12: Detection results in bottom sheet are plain text labels only
**File:** `ui/src/main/java/com/gemofgemma/ui/camera/CameraScreen.kt` — `ResultsSheet`  
**Issue:** Detected objects render as `Text(text = det.label)` in a small card. No confidence score, no color coding matching the bounding box overlay colors, no thumbnail crop of the detection region.  
**Fix:** Match `categoryColors` from `BoundingBoxOverlay.kt` to each detection in the list. Show confidence as a subtle badge.

### P2-13: Deep linking not configured
**File:** `app/src/main/java/com/gemofgemma/navigation/NavGraph.kt`  
**Issue:** No `deepLinks` defined on any `composable()` routes. The app can't be opened from notifications, share intents, or assistant triggers to a specific screen.  
**Fix:** Add deeplink URIs like `gemofgemma://chat`, `gemofgemma://camera/detect`.

### P2-14: Input bar padding inconsistency
**File:** `ui/src/main/java/com/gemofgemma/ui/chat/ChatScreen.kt` ~L352-356  
**Issue:** The `Surface` wrapping `ChatInputBar` has `tonalElevation = 3.dp` while the bottom nav has `tonalElevation = 2.dp`. This creates a subtle but visible seam between input bar and nav bar — two different shades of "surface."  
**Fix:** Match elevations, or add a subtle top border/divider to the input bar.

---

## Summary of Counts

| Priority | Count | Theme |
|----------|-------|-------|
| **P0** | 8 | Broken features, visual lies, dead interactions |
| **P1** | 13 | Missing standard features, functional gaps |
| **P2** | 14 | Polish, delight, professional finish |
| **Total** | **35** | |

## Recommended Fix Order

1. **P0-1** (error state) + **P0-6** (copy) + **P0-2** (quotes) — Quick wins, 1-2 hours
2. **P0-3** (permissions) + **P0-4** (dark mode) + **P1-9** (a11y switch) — Settings honesty pass, 2 hours
3. **P0-5** (bbox dims) + **P0-7** (shutter) + **P0-8** (copy/share) — Camera polish, 2 hours
4. **P1-1** (markdown) — Biggest single UX improvement, 3-4 hours
5. **P1-7** (transitions) + **P1-5** (settings title) — Navigation feel, 1 hour
6. **P1-2** (timestamps) + **P1-3** (scroll FAB) + **P1-4** (new chat) — Chat maturity, 2-3 hours
7. **P1-6** (VQA input) — Camera feature completeness, 1 hour
8. **P2-**** — Sprinkle over time as polish passes

---

*"A prototype tells you what the product does. A product tells you how the product feels." — This app tells us what. We need it to tell us how.*
