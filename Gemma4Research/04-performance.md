# Gemma 4 — Performance & Optimization for Mobile

## Model File Sizes (LiteRT-LM Format)

| Model | File Size | Notes |
|-------|-----------|-------|
| Gemma 4 E2B | **2.58 GB** | Includes 0.79GB weights + 1.12GB embeddings (memory-mapped) |
| Gemma 4 E4B | **3.65 GB** | Larger but more capable |

---

## Inference Benchmarks — Gemma 4 E2B

All benchmarks: 1024 prefill tokens, 256 decode tokens, 2048 context length.
The model supports up to 128K context length.

### Android

| Device | Backend | Prefill (tok/s) | Decode (tok/s) | Time-to-First (s) | Model Size (MB) | Memory (MB) |
|--------|---------|----------------|----------------|-------------------|-----------------|-------------|
| Samsung S26 Ultra | CPU | 557 | 47 | 1.8 | 2583 | 1733 |
| Samsung S26 Ultra | GPU | 3,808 | 52 | 0.3 | 2583 | 676 |

### iOS (for reference)

| Device | Backend | Prefill | Decode | TTFT | Size | Memory |
|--------|---------|---------|--------|------|------|--------|
| iPhone 17 Pro | CPU | 532 | 25 | 1.9 | 2583 | 607 |
| iPhone 17 Pro | GPU | 2,878 | 57 | 0.3 | 2583 | 1450 |

### Desktop (for reference)

| Device | Backend | Prefill | Decode | TTFT | Size | Memory |
|--------|---------|---------|--------|------|------|--------|
| MacBook Pro M4 Max | CPU | 901 | 42 | 1.1 | 2583 | 736 |
| MacBook Pro M4 Max | GPU | 7,835 | 160 | 0.1 | 2583 | 1623 |
| RTX 4090 (Linux) | GPU | 11,234 | 143 | 0.1 | 2583 | 913 |

### IoT

| Device | Backend | Prefill | Decode | TTFT | Size | Memory |
|--------|---------|---------|--------|------|------|--------|
| Raspberry Pi 5 16GB | CPU | 133 | 8 | 7.8 | 2583 | 1546 |
| Jetson Orin Nano | CPU | 109 | 12 | 9.4 | 2583 | 3681 |
| Jetson Orin Nano | GPU | 1,142 | 24 | 0.9 | 2583 | 2739 |
| Qualcomm Dragonwing IQ8 | NPU | 3,747 | 32 | 0.3 | 2967 | 1869 |

---

## Inference Benchmarks — Gemma 4 E4B

| Device | Backend | Prefill (tok/s) | Decode (tok/s) |
|--------|---------|----------------|----------------|
| Samsung S26 Ultra | CPU | 195 | 18 |
| Samsung S26 Ultra | GPU | 1,293 | 22 |
| iPhone 17 Pro | CPU | 159 | 10 |
| iPhone 17 Pro | GPU | 1,189 | 25 |
| MacBook Pro M4 | CPU | 277 | 27 |
| MacBook Pro M4 | GPU | 2,560 | 101 |

---

## Object Detection Latency Estimates

For a typical object detection query with Gemma 4 E2B on Samsung S26 Ultra:

### Estimate Breakdown
- **Image encoding**: Variable based on token budget (70-1120 visual tokens)
- **Prompt tokens**: ~10-20 tokens for detection prompt
- **Response tokens**: ~50-200 tokens for JSON with 3-10 objects
- **Time-to-first-token (GPU)**: ~0.3s
- **Decode time (GPU, ~100 tokens)**: ~2s
- **Total estimated**: **~2-3 seconds per detection** on GPU

### Real-Time Implications
- ~0.3-0.5 FPS with GPU backend on flagship devices
- NOT suitable for real-time video at 30fps
- Suitable for: photo analysis, periodic scanning, user-triggered detection
- For real-time: consider hybrid approach with traditional detector

---

## Memory Usage Analysis

### Gemma 4 E2B Working Memory (Android)

| Backend | Peak Memory (MB) | Notes |
|---------|------------------|-------|
| CPU | 1,733 | Higher due to full weight loading |
| GPU | 676 | Lower thanks to memory-mapped embeddings |

Key insight: GPU backend uses significantly less system memory because embeddings are memory-mapped and only the 0.79GB of decoder weights stay in memory. Vision and audio models are loaded on-demand.

### Memory Optimization Features
- **Memory-mapped PLE embeddings**: Large embedding tables are memory-mapped, not fully loaded into RAM
- **On-demand modality loading**: Vision and audio encoders loaded only when needed
- **LiteRT-LM caching**: Setting `cacheDir` improves subsequent load times
- **2-bit and 4-bit weight support**: Run with <1.5GB memory on some devices

---

## Quantization Options

| Format | Precision | Size Reduction | Quality Impact |
|--------|-----------|---------------|----------------|
| BF16 | Full | Baseline | Best quality |
| INT8 | 8-bit | ~50% | Minimal quality loss |
| INT4 | 4-bit | ~66% | Noticeable but usable |
| 2-bit | 2-bit | ~75% | Significant quality loss |

LiteRT-LM models on HuggingFace are pre-quantized and optimized. The `.litertlm` file format already includes optimized weights.

---

## Hardware Acceleration Backends

| Backend | Android | iOS | Notes |
|---------|---------|-----|-------|
| CPU (XNNPack) | ✅ | ✅ | Reliable fallback, 4 threads |
| GPU (ML Drift) | ✅ | ✅ | Best balance of speed/memory |
| NPU | ✅ (select devices) | ❌ | Fastest on supported hardware |

### NPU Support (Android)
```kotlin
val engineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
)
```

---

## Performance Best Practices

1. **Use GPU backend** — Best speed/memory tradeoff on mobile
2. **Set cacheDir** — Reduces model load time on subsequent launches
3. **Initialize on background thread** — `engine.initialize()` takes ~10 seconds
4. **Use streaming responses** — Show partial results via `sendMessageAsync`
5. **Lower token budget for speed** — Use 70-280 for faster detection at lower accuracy
6. **Pre-warm the model** — Run a dummy query after initialization
7. **Manage lifecycle** — Close engine/conversation when not needed
8. **Memory-mapped model** — Store model on device storage, not in assets

---

## Battery Considerations

- GPU inference draws significant power but finishes faster
- CPU inference is slower but may be gentler on battery for sustained use
- For periodic detection (e.g., every 5 seconds), GPU burst is preferred
- NPU is most power-efficient where available
- Consider user controls for detection frequency
