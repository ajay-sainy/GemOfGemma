# Real SDK Research: Running Gemma On-Device on Android

**Researcher:** Elaine (ML Engineer)  
**Date:** 2026-04-18  
**Status:** VERIFIED — Maven coordinates confirmed

---

## RECOMMENDATION: Use LiteRT-LM

### EXACT Dependency (build.gradle.kts)

```kotlin
// In gradle repositories (settings.gradle.kts), ensure google() is present:
repositories {
    google()   // Required — this is on Google Maven, NOT Maven Central
    mavenCentral()
}

// In your module's build.gradle.kts:
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")
}
```

**Group:** `com.google.ai.edge.litertlm`  
**Artifact:** `litertlm-android`  
**Latest Version:** `0.10.2` (released 2026-04-17)  
**Maven Metadata URL:** https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml  
**All published versions:** 0.0.0-alpha06, 0.8.0, 0.9.0-alpha01→alpha06, 0.9.0-beta, 0.9.0, 0.10.0, 0.10.2

> **CRITICAL NOTE:** The fake coordinate `com.google.ai.edge.litert:litert-lm:1.0.1` we used does NOT exist. The real one is `com.google.ai.edge.litertlm:litertlm-android:0.10.2`. Note the different group ID and artifact name — it's `litertlm` (no hyphen, no colon separation between litert and lm).

### EXACT API Usage (Kotlin)

```kotlin
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

// 1. Initialize the Engine
val engineConfig = EngineConfig(
    modelPath = "/data/local/tmp/llm/gemma-4-E2B-it-int4.litertlm",
    backend = Backend.GPU(),          // GPU, CPU, or NPU
    visionBackend = Backend.GPU(),    // For multimodal (image) support
    cacheDir = context.cacheDir.path  // Improves 2nd load time
)
val engine = Engine(engineConfig)
engine.initialize()  // ⚠️ Takes ~10s, call on background thread!

// 2. Create a Conversation
val conversationConfig = ConversationConfig(
    systemInstruction = Contents.of("You are a helpful assistant."),
    initialMessages = listOf(
        Message.user("Hello"),
        Message.model("Hi! How can I help?"),
    ),
    samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
)
val conversation = engine.createConversation(conversationConfig)

// 3a. Synchronous text generation
val response: Message = conversation.sendMessage("What is the capital of France?")
println(response.text)

// 3b. Async streaming with Kotlin Flow
conversation.sendMessageAsync("Tell me a story.")
    .catch { e -> /* handle error */ }
    .collect { message -> print(message.toString()) }

// 3c. Async streaming with callback
conversation.sendMessageAsync("Tell me a story.", object : MessageCallback {
    override fun onMessage(message: Message) { print(message) }
    override fun onDone() { /* streaming completed */ }
    override fun onError(throwable: Throwable) { /* error */ }
})

// 4. Multimodal (vision) — requires model with vision support (Gemma 3n, Gemma 4)
conversation.sendMessage(Contents.of(
    Content.ImageFile("/path/to/image.jpg"),
    Content.Text("Describe this image."),
))

// 5. Cleanup
conversation.close()
engine.close()
```

### AndroidManifest.xml for GPU backend

```xml
<application>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

### What Model File to Download & Where to Put It

| Model | Size | Format | HuggingFace URL | Vision | Audio |
|-------|------|--------|-----------------|--------|-------|
| **Gemma 4 E2B** | 2.58 GB | `.litertlm` | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) | ✅ | ✅ |
| **Gemma 4 E4B** | 3.65 GB | `.litertlm` | litert-community/gemma-4-E4B-it-litert-lm | ✅ | ✅ |
| **Gemma 3n E2B** | ~2.97 GB | `.litertlm` | [google/gemma-3n-E2B-it-litert-lm](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm) | ✅ | ✅ |
| **Gemma 3n E4B** | ~4.24 GB | `.litertlm` | google/gemma-3n-E4B-it-litert-lm | ✅ | ✅ |
| **Gemma 3 1B** | ~1.0 GB | `.litertlm` | [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT) | ❌ | ❌ |

**Deployment path for development:**
```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma-4-E2B-it-int4.litertlm /data/local/tmp/llm/
```

**For production:** Host the model on a server and download at runtime. The model is too large to bundle in an APK.

---

## Comparison: Our Stubs vs Real API

### What Our Stubs Got RIGHT ✅

| Aspect | Our Stub | Real API | Match? |
|--------|----------|----------|--------|
| Package name | `com.google.ai.edge.litertlm` | `com.google.ai.edge.litertlm` | ✅ Exact |
| Engine class | `Engine(config)` | `Engine(config)` | ✅ Exact |
| EngineConfig | `EngineConfig(modelPath, backend, visionBackend, cacheDir)` | Same | ✅ Exact |
| Backend sealed class | `Backend.GPU()`, `Backend.CPU()` | Same + `Backend.NPU(nativeLibraryDir)` | ✅ Close |
| Conversation | `engine.createConversation(config)` | Same | ✅ Exact |
| ConversationConfig | `systemInstruction`, `samplerConfig` | Same + `initialMessages`, `tools` | ✅ Close |
| Content sealed class | `Content.Text`, `Content.ImageBytes` | Same + more types | ✅ Close |
| Contents container | `Contents.of(text)`, `Contents.of(vararg content)` | Same | ✅ Exact |
| engine.initialize() | Present | Present | ✅ Exact |
| engine.close() | Present | Present | ✅ Exact |
| conversation.close() | Present | Present | ✅ Exact |

### What Needs Updating ⚠️

| Aspect | Our Stub | Real API | Impact |
|--------|----------|----------|--------|
| **sendMessage return type** | Returns `Contents` | Returns `Message` | 🔴 Must change |
| **Message class** | Missing | `Message` with `.text`, `.toolCalls`, factory methods `Message.user()`, `Message.model()`, `Message.tool()` | 🔴 Must add |
| **sendMessageAsync** | Missing | Returns `Flow<Message>` (coroutine) or takes `MessageCallback` | 🔴 Must add |
| **Content.ImageFile** | Missing | `Content.ImageFile(path: String)` | 🟡 Add for multimodal |
| **Content.AudioBytes** | Missing | `Content.AudioBytes(bytes: ByteArray)` | 🟡 Add for audio |
| **Content.AudioFile** | Missing | `Content.AudioFile(path: String)` | 🟡 Add for audio |
| **Content.ToolResponse** | Missing | `Content.ToolResponse(name, jsonResult)` | 🟡 Add for tools |
| **Tool use** | Missing | `@Tool`, `@ToolParam`, `ToolSet`, `OpenApiTool` | 🟡 Add for function calling |
| **Backend.NPU** | Missing | `Backend.NPU(nativeLibraryDir: String)` | 🟢 Optional |
| **SamplerConfig** | Has `topK`, `temperature` | Has `topK`, `topP`, `temperature` | 🟢 Minor |
| **ConversationConfig.initialMessages** | Missing | `listOf(Message.user(...), Message.model(...))` | 🟢 Minor |
| **ConversationConfig.tools** | Missing | `listOf(tool(MyToolSet()))` | 🟡 Add for function calling |

---

## All Three Android AI Options Compared

### Option 1: LiteRT-LM ⭐ RECOMMENDED

| Property | Value |
|----------|-------|
| **Maven Coordinate** | `com.google.ai.edge.litertlm:litertlm-android:0.10.2` |
| **Repository** | Google Maven (`google()`) |
| **Type** | On-device inference |
| **Status** | ✅ Stable, actively maintained |
| **Package** | `com.google.ai.edge.litertlm` |
| **Model Format** | `.litertlm` |
| **Gemma Support** | Gemma 4, Gemma 3n, Gemma 3 1B |
| **Vision/Multimodal** | ✅ Yes (with Gemma 3n or Gemma 4) |
| **Audio** | ✅ Yes (with Gemma 3n or Gemma 4) |
| **Tool Use / Function Calling** | ✅ Yes |
| **Streaming** | ✅ Yes (Flow + Callback) |
| **GPU Acceleration** | ✅ Yes |
| **NPU Acceleration** | ✅ Yes (Samsung, Qualcomm, MediaTek) |
| **Kotlin Coroutines** | ✅ First-class support |
| **Min Android** | High-end devices (Pixel 8+, S23+) |

### Option 2: MediaPipe LLM Inference API ⚠️ DEPRECATED

| Property | Value |
|----------|-------|
| **Maven Coordinate** | `com.google.mediapipe:tasks-genai:0.10.33` |
| **Repository** | Google Maven (`google()`) |
| **Type** | On-device inference |
| **Status** | ⚠️ DEPRECATED — "recommend migrating to LiteRT-LM" |
| **Package** | `com.google.mediapipe.tasks.genai.llminference` |
| **Model Format** | `.task` |
| **Gemma Support** | Gemma 3n, Gemma 3 1B (older models) |
| **Vision/Multimodal** | ✅ Yes (with enableVisionModality) |
| **Audio** | ✅ Yes (with enableAudioModality) |
| **Tool Use** | ❌ No |
| **Streaming** | ✅ Yes (via resultListener) |
| **API Style** | `LlmInference.createFromOptions()`, `LlmInferenceSession` |

**MediaPipe LLM Inference API code sample (for reference):**
```kotlin
val taskOptions = LlmInferenceOptions.builder()
    .setModelPath("/data/local/tmp/llm/model.task")
    .setMaxTopK(64)
    .build()
val llmInference = LlmInference.createFromOptions(context, taskOptions)
val result = llmInference.generateResponse(inputPrompt)
```

### Option 3: Google AI Client SDK (Cloud-Based) ❌ NOT On-Device

| Property | Value |
|----------|-------|
| **GitHub** | https://github.com/google/generative-ai-android |
| **Type** | Cloud API (requires internet, calls Gemini API) |
| **Note** | This is NOT on-device. Calls Google's cloud servers. |
| **Use Case** | If you want cloud-powered Gemini, not on-device Gemma |

---

## Model Format Details

### LiteRT-LM (.litertlm)
- The `.litertlm` format is the new standard for LiteRT-LM
- Contains the model weights, tokenizer, and configuration in a single file
- Models are optimized with various quantization schemes (int4, int8, etc.)
- Available from [HuggingFace litert-community](https://huggingface.co/litert-community)

### MediaPipe (.task)
- The `.task` format is used by the deprecated MediaPipe LLM Inference API
- Some models are available in both `.task` and `.litertlm` formats
- Older format, not recommended for new projects

### Other formats (NOT directly usable)
- `.safetensors` — PyTorch format, needs conversion
- `.gguf` — llama.cpp format, NOT supported by LiteRT-LM
- `.tflite` — Lower-level LiteRT format (LiteRT-LM wraps this)

---

## Vision Model Considerations for Object Detection

For our GemOfGemma app's object detection use case:

1. **Gemma 4 E2B** (2.58GB) — Best choice for vision + text
   - Supports image input natively
   - Can describe objects, answer questions about images
   - Performance: 52 tok/s decode on GPU (S26 Ultra)

2. **Gemma 3n E2B** (2.97GB) — Also supports vision + audio + text
   - Multimodal: text, image, video, audio input
   - Can process images at 256x256, 512x512, or 768x768 resolution
   - Each image encoded to 256 tokens
   - Performance: 16 tok/s decode on GPU (S24 Ultra)

3. **Gemma 3 1B** (1.0GB) — Text-only, NO vision support
   - Smallest model, fastest inference
   - Does NOT support image input
   - NOT suitable for object detection

### How to Send Images for Object Detection

```kotlin
// Using LiteRT-LM with Gemma 4 E2B for object detection
val engineConfig = EngineConfig(
    modelPath = "/data/local/tmp/llm/gemma-4-E2B-it-int4.litertlm",
    backend = Backend.GPU(),
    visionBackend = Backend.GPU(),
    cacheDir = context.cacheDir.path
)
val engine = Engine(engineConfig)
engine.initialize()

val conversation = engine.createConversation(ConversationConfig(
    systemInstruction = Contents.of(
        "You are an object detection system. For each object you detect, " +
        "return a JSON array with objects containing 'label' and 'box_2d' " +
        "(as [ymin, xmin, ymax, xmax] in 0-1000 range)."
    ),
    samplerConfig = SamplerConfig(topK = 10, temperature = 0.3),
))

// Send image + text prompt
val response = conversation.sendMessage(Contents.of(
    Content.ImageFile("/path/to/camera_frame.jpg"),
    Content.Text("Detect all visible objects in this image. Return JSON only."),
))

// Parse response.text for JSON detection results
val detections = parseDetectionJson(response.text)
```

---

## Performance Benchmarks (Gemma 4 E2B on LiteRT-LM)

| Device | Backend | Prefill (tok/s) | Decode (tok/s) | TTFT (s) | Memory (MB) |
|--------|---------|-----------------|----------------|----------|-------------|
| Samsung S26 Ultra | CPU | 557 | 47 | 1.8 | 1,733 |
| Samsung S26 Ultra | GPU | 3,808 | 52 | 0.3 | 676 |
| iPhone 17 Pro | CPU | 532 | 25 | 1.9 | 607 |
| iPhone 17 Pro | GPU | 2,878 | 57 | 0.3 | 1,450 |
| MacBook Pro M4 | CPU | 901 | 42 | 1.1 | 736 |
| MacBook Pro M4 | GPU | 7,835 | 160 | 0.1 | 1,623 |
| Raspberry Pi 5 | CPU | 133 | 8 | 7.8 | 1,546 |

---

## Migration Plan: Stubs → Real SDK

### Step 1: Update build.gradle.kts
```diff
dependencies {
-    // LiteRT-LM (Gemma 4 runtime) — placeholder until official SDK is available on Maven
-    // implementation(libs.litert.lm)
-    // implementation(libs.litert.lm.gpu)
+    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")
}
```

### Step 2: Delete stub files
Delete all files under `ai/src/main/java/com/google/ai/edge/litertlm/`:
- `Engine.kt`
- `EngineConfig.kt`
- `Conversation.kt`
- `ConversationConfig.kt`
- `Content.kt`
- `Contents.kt`
- `Backend.kt`
- `SamplerConfig.kt`

### Step 3: Update GemmaEngine.kt
- Change `sendMessage()` return type from `Contents` to `Message`
- Use `.text` property on `Message` to get the response string
- Add `sendMessageAsync()` for streaming with Flow

### Step 4: Update GemmaService.kt & GemmaServiceConnector.kt
- Adjust any code that reads `Contents.toString()` to use `Message.text`

### Step 5: Download model file
```bash
# Download Gemma 4 E2B (2.58GB) from HuggingFace
# Requires accepting Gemma license on HuggingFace
huggingface-cli download litert-community/gemma-4-E2B-it-litert-lm \
    gemma-4-E2B-it-int4.litertlm --local-dir ./models/

# Push to device
adb shell mkdir -p /data/local/tmp/llm/
adb push models/gemma-4-E2B-it-int4.litertlm /data/local/tmp/llm/
```

---

## Key Discoveries & Surprises

1. **The package name `com.google.ai.edge.litertlm` was correct all along** — our stubs used the right package. The Maven coordinate just needed `litertlm-android` as the artifact.

2. **The MediaPipe LLM Inference API is officially DEPRECATED** as of the latest docs (2026-03-31). Google recommends migrating to LiteRT-LM.

3. **Gemma 4 is the latest model** — supports vision, audio, and text. The `E2B` variant (effective 2B parameters) is recommended for mobile.

4. **Model file size is 2.58GB** — too large for APK bundling, must be downloaded at runtime or pushed via adb.

5. **GPU backend requires native library declarations** in AndroidManifest.xml.

6. **Tool use (function calling) is built into LiteRT-LM** via `@Tool` and `@ToolParam` annotations — perfect for our PhoneActionToolSet pattern.

7. **The API is very Kotlin-friendly** — supports coroutines, Flow, AutoCloseable, etc.

8. **Neither `com.google.ai.edge` nor `com.google.ai.client` groups exist on Maven Central** — both LiteRT-LM and MediaPipe are Google Maven only.
