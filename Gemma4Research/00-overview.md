# Gemma 4 — Executive Summary

**Researcher:** Elaine (ML Engineer)
**Date:** April 17, 2026
**Status:** Research Complete

---

## What is Gemma 4?

Gemma 4 is Google DeepMind's most capable family of open-weight models, released April 2, 2026. Built from the same research and technology as Gemini 3, it delivers "unprecedented intelligence-per-parameter." It is released under an **Apache 2.0 license** (commercially permissive, fully open-source).

Gemma 4 is a **multimodal** model family — it handles **text, image, video, and audio** input and generates **text** output. It is NOT a text-only model.

## Model Variants (4 Sizes)

| Variant | Total Params | Effective/Active Params | Architecture | Modalities | Context Window | Target Hardware |
|---------|-------------|------------------------|--------------|------------|----------------|-----------------|
| **E2B** | 5.1B (with embeddings) | 2.3B effective | Dense + PLE | Text, Image, Video, Audio | 128K | Mobile, Edge, IoT |
| **E4B** | 8B (with embeddings) | 4.5B effective | Dense + PLE | Text, Image, Video, Audio | 128K | Mobile, Edge |
| **26B A4B** | 25.2B | 3.8B active | Mixture-of-Experts (MoE) | Text, Image, Video | 256K | Consumer GPUs |
| **31B** | 30.7B | 30.7B | Dense | Text, Image, Video | 256K | Workstations, Servers |

- "E" = "Effective" parameters — uses Per-Layer Embeddings (PLE) for memory efficiency
- "A" = "Active" parameters — MoE architecture activates only a subset during inference

## Key Capabilities

1. **Multimodal Vision** — Image understanding, OCR, chart comprehension, variable resolution, **object detection with bounding boxes**
2. **Audio** — Speech recognition (ASR), speech translation (E2B and E4B only)
3. **Video** — Analyze video by processing frame sequences (up to 60s at 1fps)
4. **Reasoning** — Built-in thinking mode with `<|think|>` token, step-by-step reasoning
5. **Function Calling** — Native support for structured tool use, agentic workflows
6. **Coding** — Code generation, completion, correction
7. **140+ Languages** — Multilingual support with cultural context understanding
8. **System Prompts** — Native `system` role support

## Object Detection — YES, IT WORKS

**Critical finding:** Gemma 4 can perform object detection natively. It outputs bounding box coordinates as JSON in the format:
```json
[
  {"box_2d": [y1, x1, y2, x2], "label": "person"},
  {"box_2d": [y1, x1, y2, x2], "label": "cat"}
]
```
Coordinates are normalized to a 1000×1000 grid and need to be rescaled to original image dimensions.

This is a prompt-based approach: you send an image and say "detect person and cat" and get structured JSON bounding boxes back.

## Mobile/On-Device Support — EXCELLENT

- **E2B model file size: 2.58 GB** (fits on modern smartphones)
- Runs via **LiteRT-LM** framework (Google's production-ready on-device LLM runtime)
- Gradle dependency: `com.google.ai.edge.litertlm:litertlm-android:latest.release`
- Supports CPU, GPU, and NPU backends on Android
- Samsung S26 Ultra: **47 tokens/sec decode** (CPU), **52 tokens/sec** (GPU)
- Minimum Android 12

## Verdict for Our Project

Gemma 4 is a strong candidate for our Android object detection app. The E2B variant is purpose-built for mobile, supports vision/object detection natively, and has a mature Android deployment path via LiteRT-LM. The main consideration is that it's a generative model (outputs text/JSON) rather than a traditional detector, which affects latency characteristics.
