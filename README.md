# Gem of Gemma 💎

### On-Device AI Assistant for Android — Powered by Gemma 4

![Android](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![LiteRT-LM](https://img.shields.io/badge/LiteRT--LM-0.10.2-FF6F00?logo=google&logoColor=white)

An open-source Android app showcasing **on-device AI inference** with [Gemma 4](https://blog.google/technology/developers/gemma-4/) and [LiteRT-LM](https://ai.google.dev/edge/litert-lm). Chat, understand images, and control your phone — all running locally with **zero internet** after the initial model download. Entirely vibe coded with [GitHub Copilot](https://github.com/features/copilot).

No cloud APIs. No subscriptions. No data leaving your device. This is private, portable AI running on your phone's hardware.

> **Keywords:** Gemma 4, on-device LLM, Android AI, LiteRT-LM, offline AI assistant, on-device inference, Jetpack Compose, function calling, multimodal AI, object detection, OCR, image captioning, visual question answering, speech to text, phone automation, Material 3, Kotlin, open source

## 📸 Screenshots

<p align="center">
  <img src="screenshots/chat-home.png" width="220" alt="Gem of Gemma chat home screen with suggestion chips" />
  <img src="screenshots/chat-response.png" width="220" alt="Gemma 4 native function calling - set alarm tool" />
  <img src="screenshots/thinking-mode.png" width="220" alt="Gemma 4 thinking mode with chain of thought reasoning" />
</p>
<p align="center">
  <img src="screenshots/image-caption.png" width="220" alt="On-device OCR and image understanding with Gemma 4" />
  <img src="screenshots/tool-picker.png" width="220" alt="22 toggleable phone automation tools" />
</p>

## What It Can Do

- **Chat** — Natural conversation with real-time token streaming and visible thinking/reasoning, powered by Gemma 4 E2B running entirely on-device
- **See** — Multimodal image understanding from camera or gallery: describe scenes, detect objects with bounding boxes, read text (OCR), answer visual questions
- **Control your phone** — 22 toggleable tools via LiteRT-LM's native ToolSet API: send SMS, make calls, set alarms, toggle flashlight, adjust volume/brightness, navigate, control media, and more
- **Voice input** — On-device speech recognition for hands-free interaction
- **Persistent conversations** — Chat history saved locally, multiple conversations supported

## Getting Started

```bash
git clone https://github.com/ajay-sainy/GemOfGemma.git
cd GemOfGemma
./gradlew installDebug
```

**Requirements:** Android Studio, JDK 17+, Android device with 4GB+ RAM, ~3GB storage.

On first launch, the app downloads **Gemma 4 E2B** from HuggingFace (~2.5 GB, one-time). After that, it runs fully offline — no internet needed.

## How It Works

The app uses [LiteRT-LM](https://ai.google.dev/edge/litert-lm) to run Google's Gemma 4 model directly on Android hardware. Key technical highlights:

- **Streaming inference** via `Conversation.sendMessageAsync()` — tokens appear in real-time
- **Native function calling** via LiteRT-LM's `ToolSet` API with `@Tool` annotations
- **Thinking mode** with `Channel("thinking")` — visible chain-of-thought reasoning
- **Format-based response parsing** — model outputs `` ```json `` with `box_2d` for object detection (following [Google's official approach](https://ai.google.dev/gemma/docs/capabilities/vision/image))
- **Multi-module architecture** — `:app`, `:ui`, `:ai`, `:core`, `:actions`, `:camera`, `:voice`, `:accessibility`

## Model License

The Gemma model is subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms). This project's source code is [Apache 2.0](LICENSE).

## Contributing

Contributions welcome — open an issue first to discuss, then submit a PR.

## Acknowledgments

[Google DeepMind](https://deepmind.google/) (Gemma) · [Google AI Edge](https://ai.google.dev/edge) (LiteRT-LM) · [Jetpack Compose](https://developer.android.com/compose)
