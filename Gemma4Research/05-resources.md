# Gemma 4 — Resources, URLs, and References

## Official Google Pages

| Resource | URL |
|----------|-----|
| Gemma 4 Overview | https://ai.google.dev/gemma/docs/core |
| Gemma 4 Model Card | https://ai.google.dev/gemma/docs/core/model_card_4 |
| Gemma 4 Landing Page | https://deepmind.google/models/gemma/gemma-4/ |
| Gemma 4 Launch Blog | https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/ |
| Gemma 4 License (Apache 2.0) | https://ai.google.dev/gemma/apache_2 |
| Gemma Models Hub | https://deepmind.google/models/gemma/ |
| Gemma Docs Home | https://ai.google.dev/gemma/docs |
| Gemma Get Started | https://ai.google.dev/gemma/docs/get_started |

## Vision & Object Detection

| Resource | URL |
|----------|-----|
| Image Understanding Guide (with OD code) | https://ai.google.dev/gemma/docs/capabilities/vision/image |
| Video Understanding | https://ai.google.dev/gemma/docs/capabilities/vision/video |
| Vision Cookbook (Colab) | https://colab.research.google.com/github/google-gemma/cookbook/blob/main/docs/capabilities/vision/image.ipynb |

## Android & Mobile Integration

| Resource | URL |
|----------|-----|
| Deploy Gemma on Mobile | https://ai.google.dev/gemma/docs/integrations/mobile |
| LiteRT-LM Overview | https://ai.google.dev/edge/litert-lm/overview |
| LiteRT-LM Android/Kotlin Guide | https://ai.google.dev/edge/litert-lm/android |
| LiteRT-LM CLI | https://ai.google.dev/edge/litert-lm/cli |
| LiteRT-LM Python | https://ai.google.dev/edge/litert-lm/python |
| LiteRT-LM C++ | https://ai.google.dev/edge/litert-lm/cpp |
| LiteRT-LM API Reference | https://ai.google.dev/edge/litert-lm/reference/api |
| LiteRT-LM FAQ | https://ai.google.dev/edge/litert-lm/reference/faq |
| MediaPipe LLM Inference (Android) | https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android |
| Google AI Edge Overview | https://ai.google.dev/edge |
| Android AICore Developer Preview | https://android-developers.googleblog.com/2026/03/AI-Core-Developer-Preview |

## GitHub Repositories

| Repository | URL | Stars |
|-----------|-----|-------|
| Google AI Edge Gallery (reference app) | https://github.com/google-ai-edge/gallery | 21.4k |
| LiteRT-LM (inference framework) | https://github.com/google-ai-edge/LiteRT-LM | 3.9k |
| Gemma Cookbook | https://github.com/google-gemma/cookbook | — |
| MediaPipe Samples | https://github.com/google-ai-edge/mediapipe-samples | — |
| Google AI Edge (org) | https://github.com/google-ai-edge | — |

## Model Downloads

### LiteRT-LM Format (for mobile deployment)

| Model | URL | Size |
|-------|-----|------|
| Gemma 4 E2B (LiteRT-LM) | https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm | 2.58 GB |
| Gemma 4 E4B (LiteRT-LM) | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm | 3.65 GB |
| LiteRT Community Collection | https://huggingface.co/litert-community | — |

### Hugging Face (Transformers format)

| Model | URL |
|-------|-----|
| Gemma 4 E2B IT | https://huggingface.co/google/gemma-4-E2B-it |
| Gemma 4 E4B IT | https://huggingface.co/google/gemma-4-E4B-it |
| Gemma 4 31B IT | https://huggingface.co/google/gemma-4-31B-it |
| Gemma 4 26B A4B IT | https://huggingface.co/google/gemma-4-26B-A4B-it |
| Gemma 4 Collection | https://huggingface.co/collections/google/gemma-4 |

### Other Platforms

| Platform | URL |
|----------|-----|
| Kaggle | https://www.kaggle.com/models/google/gemma-4 |
| Ollama | https://ollama.com/library/gemma4 |
| LM Studio | https://lmstudio.ai/models/gemma-4 |

## Blog Posts & Announcements

| Title | URL | Date |
|-------|-----|------|
| Gemma 4: Byte for byte, the most capable open models | https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/ | Apr 2, 2026 |
| Bring state-of-the-art agentic skills to the edge with Gemma 4 | https://developers.googleblog.com/en/bring-state-of-the-art-agentic-skills-to-the-edge-with-gemma-4/ | Apr 2, 2026 |
| HuggingFace Gemma 4 Blog | https://huggingface.co/blog/gemma4 | Apr 2, 2026 |

## SDK & Dependencies

### Gradle (Android)
```kotlin
// LiteRT-LM (recommended)
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

// MediaPipe LLM (legacy, deprecated)
implementation("com.google.mediapipe:tasks-genai:0.10.27")
```

### Maven Coordinates
- LiteRT-LM Android: https://maven.google.com/web/index.html#com.google.ai.edge.litertlm:litertlm-android
- LiteRT-LM JVM: https://maven.google.com/web/index.html#com.google.ai.edge.litertlm:litertlm-jvm

## Tools & Demos

| Tool | URL |
|------|-----|
| Google AI Studio (try Gemma 4 31B) | https://aistudio.google.com/prompts/new_chat?model=gemma-4-31b-it |
| Google AI Edge Gallery (Play Store) | https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery |
| Gemma 4 Web Demo (WebGPU) | https://huggingface.co/spaces/tylermullen/Gemma4 |
| MediaPipe Studio | https://mediapipe-studio.webapps.google.com/studio/demo/llm_inference |

## Fine-Tuning Resources

| Resource | URL |
|----------|-----|
| Text Fine-Tune with QLoRA | https://ai.google.dev/gemma/docs/core/huggingface_text_finetune_qlora |
| Vision Fine-Tune with QLoRA | https://ai.google.dev/gemma/docs/core/huggingface_vision_finetune_qlora |
| Full Fine-Tune (HF Transformers) | https://ai.google.dev/gemma/docs/core/huggingface_text_full_finetune |
| Convert HF to MediaPipe Task | https://ai.google.dev/gemma/docs/conversions/hf-to-mediapipe-task |
| Gemma Library (JAX fine-tuning) | https://gemma-llm.readthedocs.io/en/latest/colab_finetuning.html |

## Additional Capabilities Documentation

| Capability | URL |
|-----------|-----|
| Audio Support | https://ai.google.dev/gemma/docs/capabilities/audio |
| Thinking Mode | https://ai.google.dev/gemma/docs/capabilities/thinking |
| Function Calling | https://ai.google.dev/gemma/docs/capabilities/text/function-calling-gemma4 |
| Prompt Formatting | https://ai.google.dev/gemma/docs/core/prompt-formatting-gemma4 |

## Related Projects / Ecosystem

| Project | URL |
|---------|-----|
| FunctionGemma (function calling) | https://huggingface.co/google/functiongemma-270m-it |
| MedGemma (medical) | Research blog |
| EmbeddingGemma (embeddings) | https://developers.googleblog.com/en/introducing-embeddinggemma/ |
| Gemmaverse (community models) | https://deepmind.google/models/gemma/gemmaverse/ |
| Gemma Discord | https://ai.google.dev/gemma/docs/discord |
