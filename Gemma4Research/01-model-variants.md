# Gemma 4 — Model Variants Deep Dive

## Architecture Overview

Gemma 4 spans three distinct architectures:
1. **Small/Edge (E2B, E4B)** — Dense models with Per-Layer Embeddings (PLE) for ultra-mobile/edge/browser
2. **Dense (31B)** — Traditional dense model bridging server performance and local execution
3. **Mixture-of-Experts (26B A4B)** — Efficient MoE for high-throughput advanced reasoning

All models use a hybrid attention mechanism: interleaved local sliding window attention + full global attention, with the final layer always global. Global layers use unified Keys/Values and Proportional RoPE (p-RoPE).

---

## Detailed Model Specifications

### Gemma 4 E2B (Mobile-Optimal ⭐)

| Property | Value |
|----------|-------|
| Total Parameters | 5.1B (with embeddings) |
| Effective Parameters | 2.3B |
| Decoder Layers | 35 |
| Sliding Window | 512 tokens |
| Context Length | 128K tokens |
| Vocabulary Size | 262K |
| Modalities | Text, Image, Audio |
| Vision Encoder | ~150M params |
| Audio Encoder | ~300M params |
| LiteRT-LM File Size | **2.58 GB** |
| Text Decoder Weights | 0.79 GB |
| Embedding Parameters | 1.12 GB (memory-mapped) |

**Why "Effective"?** PLE gives each decoder layer its own small embedding lookup table. These tables are large but only used for fast lookups, so the effective compute is much lower than total parameter count suggests.

**Memory Requirements:**
| Precision | Memory |
|-----------|--------|
| BF16 (default) | 9.6 GB |
| INT8 | 4.6 GB |
| INT4 | 3.2 GB |

### Gemma 4 E4B (Mobile/Edge)

| Property | Value |
|----------|-------|
| Total Parameters | 8B (with embeddings) |
| Effective Parameters | 4.5B |
| Decoder Layers | 42 |
| Sliding Window | 512 tokens |
| Context Length | 128K tokens |
| Vocabulary Size | 262K |
| Modalities | Text, Image, Audio |
| Vision Encoder | ~150M params |
| Audio Encoder | ~300M params |
| LiteRT-LM File Size | ~3.65 GB |

**Memory Requirements:**
| Precision | Memory |
|-----------|--------|
| BF16 | 15 GB |
| INT8 | 7.5 GB |
| INT4 | 5 GB |

### Gemma 4 31B Dense (Server/Workstation)

| Property | Value |
|----------|-------|
| Total Parameters | 30.7B |
| Decoder Layers | 60 |
| Sliding Window | 1024 tokens |
| Context Length | 256K tokens |
| Vocabulary Size | 262K |
| Modalities | Text, Image (NO audio) |
| Vision Encoder | ~550M params |

**Memory Requirements:**
| Precision | Memory |
|-----------|--------|
| BF16 | 58.3 GB |
| INT8 | 30.4 GB |
| INT4 | 17.4 GB |

### Gemma 4 26B A4B MoE (Server/Consumer GPU)

| Property | Value |
|----------|-------|
| Total Parameters | 25.2B |
| Active Parameters | 3.8B per token |
| Expert Count | 8 active / 128 total + 1 shared |
| Decoder Layers | 30 |
| Sliding Window | 1024 tokens |
| Context Length | 256K tokens |
| Vocabulary Size | 262K |
| Modalities | Text, Image (NO audio) |
| Vision Encoder | ~550M params |

**Memory Requirements:**
| Precision | Memory |
|-----------|--------|
| BF16 | 48 GB |
| INT8 | 25 GB |
| INT4 | 15.6 GB |

Note: All 25.2B parameters must be loaded into memory even though only 3.8B are active per token.

---

## Mobile-Friendly Variants Ranking

For our Android object detection project, ranked by suitability:

### 1. 🏆 Gemma 4 E2B — Best Fit
- 2.58 GB file size fits on modern Android phones
- Runs at 47 tok/s (CPU) or 52 tok/s (GPU) on Samsung S26 Ultra
- Full vision + audio multimodal support
- <1.5 GB working memory with memory-mapped embeddings
- Object detection with bounding boxes confirmed working

### 2. 🥈 Gemma 4 E4B — Viable for High-End Devices
- 3.65 GB file size — requires flagship devices with ≥8GB RAM
- Better accuracy than E2B at cost of higher memory
- Also supports vision + audio
- May be tight on mid-range devices

### 3. ❌ Gemma 4 31B / 26B A4B — NOT mobile-suitable
- 17-58 GB memory requirements
- Designed for desktops and servers
- No audio support

---

## Benchmark Performance (Instruction-Tuned)

| Benchmark | 31B | 26B A4B | E4B | E2B |
|-----------|-----|---------|-----|-----|
| MMLU Pro | 85.2% | 82.6% | 69.4% | 60.0% |
| AIME 2026 (math) | 89.2% | 88.3% | 42.5% | 37.5% |
| GPQA Diamond (science) | 84.3% | 82.3% | 58.6% | 43.4% |
| MMMU Pro (multimodal) | 76.9% | 73.8% | 52.6% | 44.2% |
| τ2-bench (agentic) | 86.4% | 85.5% | 57.5% | 29.4% |
| LiveCodeBench v6 | 80.0% | 77.1% | 52.0% | 44.0% |
| MMMLU (multilingual) | 88.4% | 86.3% | 76.6% | 67.4% |

The 31B model ranks #3 among open models on Arena AI text leaderboard (Elo 1452). It outcompetes models 20x its size.

---

## Download Sources

- **Hugging Face:** https://huggingface.co/collections/google/gemma-4
- **Kaggle:** https://www.kaggle.com/models/google/gemma-4
- **Ollama:** https://ollama.com/library/gemma4
- **LM Studio:** https://lmstudio.ai/models/gemma-4
- **LiteRT format (for mobile):** https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
