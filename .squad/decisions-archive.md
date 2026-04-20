# Decisions Archive

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

