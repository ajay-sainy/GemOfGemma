# Gemma 4 — Android Integration Guide

## Overview of Integration Paths

There are **three** supported paths to run Gemma 4 on Android:

| Path | Status | Recommended For |
|------|--------|-----------------|
| **LiteRT-LM** (Kotlin API) | ✅ Stable, Recommended | In-app deployment, custom apps |
| **MediaPipe LLM Inference API** | ⚠️ Deprecated (still works) | Legacy apps, simpler API |
| **Android AICore / ML Kit GenAI** | 🆕 Developer Preview | Production apps using system model |

---

## Path 1: LiteRT-LM (RECOMMENDED)

LiteRT-LM is Google's production-ready, open-source inference framework for deploying LLMs on edge devices. It powers Google AI Edge Gallery and is the recommended path.

### Gradle Setup

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
```

Available versions at: https://maven.google.com/web/index.html#com.google.ai.edge.litertlm:litertlm-android

### AndroidManifest.xml (GPU support)

```xml
<application>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

### Initialize Engine

```kotlin
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

val engineConfig = EngineConfig(
    modelPath = "/path/to/gemma-4-E2B-it.litertlm",
    backend = Backend.GPU(), // Or Backend.CPU(), Backend.NPU(...)
    visionBackend = Backend.GPU(), // For image/video input
    // cacheDir = context.cacheDir.path // Improves 2nd load time
)

val engine = Engine(engineConfig)
engine.initialize() // ⚠️ Call on background thread! Can take ~10 seconds
```

### Create Conversation

```kotlin
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

val conversationConfig = ConversationConfig(
    systemInstruction = Contents.of("You are a helpful assistant."),
    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
)

val conversation = engine.createConversation(conversationConfig)
```

### Send Messages (Streaming)

```kotlin
// Synchronous
val result = conversation.sendMessage("What is in this image?")

// Async with Kotlin Flow (recommended)
conversation.sendMessageAsync("What is in this image?")
    .catch { /* error handling */ }
    .collect { print(it.toString()) }

// Async with callback
conversation.sendMessageAsync("Describe this", object : MessageCallback {
    override fun onMessage(message: Message) { print(message) }
    override fun onDone() { /* done */ }
    override fun onError(throwable: Throwable) { /* error */ }
})
```

### Multimodal Input (Vision)

```kotlin
// Send image + text
conversation.sendMessage(Contents.of(
    Content.ImageFile("/path/to/image.jpg"),
    Content.Text("Describe the objects in this image."),
))

// Or with byte arrays
conversation.sendMessage(Contents.of(
    Content.ImageBytes(imageByteArray),
    Content.Text("detect all objects"),
))
```

### Tool Use / Function Calling

```kotlin
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam

class DetectionToolSet : ToolSet {
    @Tool(description = "Get the current location coordinates")
    fun getLocation(): Map<String, Any> {
        return mapOf("lat" to 37.7749, "lon" to -122.4194)
    }
}

val conversation = engine.createConversation(
    ConversationConfig(
        tools = listOf(tool(DetectionToolSet())),
    )
)
```

### Resource Management

```kotlin
// Use AutoCloseable pattern
engine.createConversation(config).use { conversation ->
    // interact with conversation
}
engine.close() // Release resources when done
```

---

## Path 2: MediaPipe LLM Inference API (Legacy)

> ⚠️ **Deprecated**: Google recommends migrating to LiteRT-LM.

### Gradle Setup

```kotlin
dependencies {
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
}
```

### Basic Usage

```kotlin
val taskOptions = LlmInferenceOptions.builder()
    .setModelPath("/data/local/tmp/llm/model.task")
    .setMaxTopK(64)
    .build()

val llmInference = LlmInference.createFromOptions(context, taskOptions)
val result = llmInference.generateResponse(inputPrompt)
```

### Vision with MediaPipe

```kotlin
import com.google.mediapipe.framework.image.BitmapImageBuilder

val mpImage = BitmapImageBuilder(bitmap).build()

val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
    .build()

session.addQueryChunk("Describe the objects in the image.")
session.addImage(mpImage)
val result = session.generateResponse()
```

---

## Path 3: Android AICore (Developer Preview)

For production apps that want to use Android's built-in, system-optimized Gemma 4 model:

- Available through **AICore Developer Preview**
- Uses **ML Kit GenAI Prompt API**
- Model is managed by the Android system (no bundling needed)
- Forward-compatible with **Gemini Nano 4**
- Blog: https://android-developers.googleblog.com/2026/03/AI-Core-Developer-Preview

---

## Model Download & Bundling

### Option A: Download at Runtime (Recommended)
- Host the `.litertlm` file on your server or use HuggingFace
- Download on first launch, cache locally
- Model file: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

### Option B: Use ADB for Development
```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/
```

### Option C: AICore (System Model)
- No download needed — model managed by Android system
- Requires AICore Developer Preview

> **Note:** At 2.58 GB, the model is too large to bundle in an APK.

---

## Minimum Requirements

| Requirement | Value |
|-------------|-------|
| Android Version | 12+ |
| Target Devices | High-end (Pixel 8+, Samsung S23+) |
| RAM | ≥6 GB recommended |
| Storage | ~3 GB free for model |
| GPU Support | Optional but recommended |

---

## Reference Apps

1. **Google AI Edge Gallery** — Full-featured reference app
   - Source: https://github.com/google-ai-edge/gallery
   - Play Store: Available on Google Play
   - Written in Kotlin, uses LiteRT-LM
   - Features: Chat, Ask Image, Prompt Lab, Agent Skills, Benchmarking

2. **LiteRT-LM Examples**
   - Source: https://github.com/google-ai-edge/LiteRT-LM
   - Kotlin example: `kotlin/java/com/google/ai/edge/litertlm/example/Main.kt`
   - Tool use example: `kotlin/java/com/google/ai/edge/litertlm/example/ToolMain.kt`
