# Squad Decisions & Architecture Log

## Core Scope
### Project Expansion & Renaming
**Date:** 2026-04-18 | **Author:** Ajay Sainy
- **Decision:** Project renamed from ObjectDetection to GemOfGemma.
- **Details:** Scope expanded to an all-in-one on-device AI assistant covering object detection, image captioning, visual Q&A, OCR, on-device chat, voice commands, and full phone automation (SMS, calls, alarms, toggles, app launching).
- **Rationale:** Gemma 4's multimodal capabilities and function-calling allow a single model to handle all features via different prompts.

## Architecture
### Dependency Injection & Modules
**Date:** 2026-04-17 to 2026-04-18 | **Authors:** Jerry (Lead), Elaine (ML Engineer)
- **Hilt for DI:** Selected for compile-time validation of 8 modules with complex cross-module dependencies. (Jerry)
- **Interface-in-Core Pattern:** Created AiProcessor interface in :core. :ui ViewModels inject this interface, uncoupling them from the :ai service implementation. (Elaine)
- **Service Binding:** GemmaServiceConnector uses MutableStateFlow<GemmaService?> to track the bound service, surviving process death and reconnects better than CompletableDeferred. (Elaine)
- **Direct UI Dependencies:** :voice and :camera are direct dependencies of :ui to avoid over-engineering abstractions. (Elaine)

### Minimum SDK Requirements
**Date:** 2026-04-17 | **Author:** Jerry (Lead)
- **API 31:** minSdkVersion 31 (Android 12) is required.
- **Rationale:** Needed for SpeechRecognizer.createOnDeviceSpeechRecognizer() to ensure voice privacy, and required by LiteRT-LM.

## AI & Data
### Model & Runtime Selection
**Date:** 2026-04-17 | **Author:** Jerry (Lead)
- **Gemma 4 E2B:** Selected as the sole on-device model (2.58 GB, 2.3B params, 52 tok/s GPU) to handle all modalities and simplify architecture.
- **LiteRT-LM:** Chosen as the inference runtime (com.google.ai.edge.litertlm:litertlm-android). It is GA, supports GPU/CPU/NPU, and has excellent tool-calling support compared to AICore (preview) or MediaPipe (deprecated).
- **Model Hosting:** LifecycleService with oregroundServiceType="specialUse" keeps the 676 MB GPU memory and 10s init alive across activity bounds.

### Autonomous Tool Execution & Safety
**Date:** 2026-04-17 to 2026-04-18 | **Authors:** Jerry (Lead), Elaine (ML Engineer)
- **Silent Capabilities Expanded:** Added environment telemetry (light, motion, battery), network context, haptics, and system settings to PhoneActionToolSet for rich context without user friction. (Elaine)
- **Action Confirmation:** External-facing actions require explicit user confirmation. (Jerry)
- **AccessibilityService:** Deferred to Phase 4. Avoids Play Store rejection risk; app must remain fully functional without it. (Jerry)

## UI & Design
### UI Quality & Vision Overhaul
**Date:** 2026-04-18 | **Authors:** Ajay Sainy, George (Android Dev)
- **Production-Grade Directive:** UI must be polished, professional, and classy. "Not a high school student app." (Ajay)
- **Vision Screen Rewrite:** Complete transition to a Google Lens-quality experience for Object Detection, Image Captioning, OCR, and Visual Q&A. (George)
- **UI Architecture:** Replaced BottomSheetScaffold with custom FrostedBottomCard for overlaid panels. Result history maintained in ViewModel state (max 10 items) instead of a database for now. Category colors determined via keyword matching. (George)

### 2026-04-19: UI Deep Code Review by Peterman

## 1. Global Scaffolding & Layout Architecture
**Finding: Duplicate Headers / Visual Clutter**
- `NavGraph.kt` implements a global `Scaffold` with a `TopAppBar` (displaying "Gem of Gemma" and a Settings icon) and a `NavigationBar`.
- However, the child screens (`ChatScreen`, `VisionHubScreen`) implement their own secondary headers. For example, `ChatScreen` has a `Row` with `AnimatedGemIcon` and "GemOfGemma" text, and `VisionHubScreen` has a manual `Spacer(32.dp)` followed by an `Icon` and "Vision" text. 
- **Impact:** When rendered, the user will see a double-header setup (the Scaffold's app bar stacked on top of the screen's custom header). This eats up vertical real estate and introduces confusing visual hierarchy.
- **Recommendation:** Either remove the global `TopAppBar` from `NavGraph` and let each screen manage its own top bar (allowing for richer, screen-specific headers), or remove the custom headers from the child screens and inject their actions into the global top bar.

**Finding: Safe Areas & Insets**
- The `NavHost` correctly applies the Scaffold's `paddingValues` to ensure content isn't obscured by the global top/bottom bars.
- `ChatScreen` excellently applies `imePadding()` to its root column, which guarantees the chat input shifts up gracefully when the software keyboard appears.
- However, manual `Spacer` usage for vertical offset (like the 32.dp spacer at the top of `VisionHubScreen`) is brittle. If dynamic insets or custom top bars are used later, relying on fixed spacers can cause overlapping or awkward gaps across different device densities.

## 2. Screen-Specific Analysis

### ChatScreen
- **Strengths:** 
  - The Empty State is beautifully designed. The use of `FlowRow` for suggestion chips and the `AnimatedGemIcon` creates a welcoming onboarding feel.
  - Chat bubbles make great use of asymmetrical rounded corners (e.g., `bottomStart = 20.dp, bottomEnd = 6.dp` for user) to indicate message direction visually without relying solely on alignment.
  - The input bar is polished—nice touch with an overlaying Surface, rounded text field, and clean state toggles (Mic vs. Send) complete with scale and fade animations.
- **Areas for Polish:**
  - The `AnimatedVisibility` for `!isModelAvailable` shifts content down when it appears. Because it's inside the main column, the sudden layout shift might disrupt the reading flow. Consider wrapping it as an overlay or pinning it more cleanly.
  - Double "Gem of Gemma" top bar constraint as mentioned above.

### VisionHubScreen
- **Strengths:**
  - The visual execution of the hub cards is top-notch. Using interaction source properties to apply a bouncy `spring` scale down (to 0.93f) on press adds delightful tactile feedback.
  - Combining `Brush.verticalGradient` with subtle horizontal accent glow bars makes the grid look premium and engaging. 
- **Areas for Polish:**
  - Layout constraint: The 2x2 grid is built using nested `Row` and `Column` elements with `modifier.weight(1f)`. While this works, a `LazyVerticalGrid` could provide better scaling behavior on tablets or foldables if more tools are added.
  - Fix the double header. The "Vision" title overlaps semantically with the bottom navigation state.

### AudioHubScreen
- **Current State:** A simple placeholder `Box` and `Text`.
- **Recommendation:** Needs to be brought up to the design standards of `VisionHubScreen`, utilizing similar interactive cards, custom gradients, and "Plus Jakarta Sans" typography.

## 3. Typography and Theming
- **Font Choice:** Using `Plus Jakarta Sans` via Google Fonts is stellar. It reads universally clean, giving the app a distinct, modern identity. The `GemTypography` scales map perfectly to Material 3 tokens (`displayLarge`, `headlineSmall`, `bodyMedium`, etc.).
- **Hierarchy:** Both screens heavily utilize semantic typography (e.g., `titleLarge` for primary headers, `bodyMedium` for text, `labelMedium` for badges). Everything appears compliant mathematically.

## Summary Verdict
The UI is built with a very strong declarative React-style mindset, full of delightful micro-interactions (`AnimatedVisibility`, Spring animations) and robust theming. The single most critical issue to resolve is the **Double Top Bar layout**. Resolving that will transform the UI from "slightly cluttered" to flawless.
