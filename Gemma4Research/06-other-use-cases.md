# Gemma 4 — Other Use Cases Beyond Object Detection

**Researcher:** Elaine (ML Engineer)
**Date:** April 17, 2026
**Status:** Research Complete

---

## Overview

Gemma 4 E2B is a multimodal vision-language model (VLM) that handles **text, image, video, and audio** input, generating text output. While our primary POC focuses on object detection, the same 2.58 GB on-device model can power a wide range of use cases — all running **completely offline** with **zero data leaving the device**.

This document catalogs use cases across six categories, grounded in capabilities confirmed by official Google documentation, the model card, and the AI Edge Gallery showcases.

---

## 1. Vision Use Cases

### 1.1 Image Captioning & Description

| Aspect | Detail |
|--------|--------|
| **What it does** | Generate natural-language descriptions of photos — from one-sentence captions to multi-paragraph narratives with scene context, mood, and compositional analysis. |
| **Why on-device** | Privacy for personal photos. No upload needed. Works offline (airplane, rural). Instant results. |
| **Feasibility** | **Easy** — This is a core capability demonstrated in official docs. Simple prompt: "Describe this image." |
| **Prompt example** | `"Write a detailed caption for this photo suitable for social media."` |
| **Limitations** | May hallucinate minor details. Creative captions vary in quality. |

### 1.2 Visual Question Answering (VQA)

| Aspect | Detail |
|--------|--------|
| **What it does** | Answer free-form questions about image content: "How many people are in this photo?", "What breed is this dog?", "Is this food vegetarian?" |
| **Why on-device** | Interactive, conversational exploration of images without network dependency. Low latency for back-and-forth Q&A. |
| **Feasibility** | **Easy** — Official docs demonstrate VQA as a primary use case. Multi-turn conversation supported. |
| **Prompt example** | `"How many chairs are at the table? What color is the tablecloth?"` |
| **Limitations** | Counting accuracy degrades for large numbers (>10 items). Fine-grained attributes (exact color shade) can be unreliable. |

### 1.3 OCR — Text Recognition (Including Multilingual)

| Aspect | Detail |
|--------|--------|
| **What it does** | Extract text from images — signs, labels, documents, handwriting. Supports multilingual OCR natively (140+ languages), including CJK, Arabic, Cyrillic. |
| **Why on-device** | Privacy for sensitive documents (IDs, medical records, contracts). Offline use for travelers reading foreign signs. |
| **Feasibility** | **Easy** — Officially demonstrated with multilingual examples (Japanese sign recognition). Use high token budget (560-1120) for best OCR accuracy. |
| **Prompt example** | `"What does the sign say?"` or `"Extract all text from this document image."` |
| **Limitations** | Very small or heavily stylized text may require max token budget. Not a replacement for production document processing pipelines at scale. |

### 1.4 Document & Receipt Scanning

| Aspect | Detail |
|--------|--------|
| **What it does** | Parse receipts, invoices, business cards, forms — extract structured data (line items, totals, dates, addresses) as JSON. |
| **Why on-device** | Financial data stays on-device. Works for field workers without connectivity. Instant expense tracking. |
| **Feasibility** | **Medium** — OCR capability is confirmed. Structured extraction requires careful prompting with JSON output format. Model card lists "Document/PDF parsing" as a core capability. |
| **Prompt example** | `"Extract the merchant name, date, line items with prices, tax, and total from this receipt as JSON."` |
| **Limitations** | Complex multi-page documents exceed single-image context. Accuracy depends on image quality and token budget. |

### 1.5 Chart & Graph Understanding

| Aspect | Detail |
|--------|--------|
| **What it does** | Interpret charts, graphs, tables, and infographics — describe trends, extract data points, answer questions about visualizations. |
| **Why on-device** | Quick analysis of business reports, dashboards, academic papers without uploading proprietary data. |
| **Feasibility** | **Medium** — Model card explicitly lists "chart comprehension" as a core capability. The OmniDocBench benchmark score (0.181 edit distance for E2B) confirms reasonable document understanding. |
| **Prompt example** | `"What trend does this bar chart show? Which category has the highest value?"` |
| **Limitations** | Very dense charts with small labels need high token budget. Precise numerical extraction may have margin of error. |

### 1.6 Image Classification & Categorization

| Aspect | Detail |
|--------|--------|
| **What it does** | Classify images into categories — "Is this a cat or dog?", "What type of cloud is this?", "Is this food safe to eat?" Open-vocabulary, no fixed class set. |
| **Why on-device** | Fast classification without internet. Can use any category taxonomy without retraining. |
| **Feasibility** | **Easy** — Simpler than object detection. Low token budget (70) sufficient. Very fast inference. |
| **Prompt example** | `"Classify this image into one of: landscape, portrait, food, architecture, animal, vehicle."` |
| **Limitations** | No confidence scores (unlike traditional classifiers). May be verbose unless constrained. |

### 1.7 Screen & UI Understanding

| Aspect | Detail |
|--------|--------|
| **What it does** | Interpret screenshots of apps, websites, or device UIs — identify buttons, read text, describe layout, enable UI automation agents. |
| **Why on-device** | Powers accessibility overlays and on-device app agents without sending screen content to cloud. Model card lists "screen and UI understanding" explicitly. |
| **Feasibility** | **Medium** — Confirmed capability. Combined with function calling, enables agentic workflows that navigate apps autonomously. |
| **Prompt example** | `"What app is shown? Identify the main action buttons and their labels."` |
| **Limitations** | Complex, nested UIs may confuse the model. Action execution requires integration with Android accessibility APIs. |

### 1.8 Pointing (Spatial Referencing)

| Aspect | Detail |
|--------|--------|
| **What it does** | Point to specific objects or regions in an image by outputting coordinates. The model card explicitly lists "pointing" as a capability. |
| **Why on-device** | Enables AR-style overlays, interactive image exploration, guided tutorials. |
| **Feasibility** | **Medium** — Listed in model card. Uses same coordinate system as object detection (1000×1000 grid). |
| **Prompt example** | `"Point to the fire extinguisher in this image."` |
| **Limitations** | Less explored than bounding box detection. May need experimentation with prompt formats. |

---

## 2. Audio & Speech Use Cases (E2B/E4B Only)

### 2.1 On-Device Speech Recognition (ASR)

| Aspect | Detail |
|--------|--------|
| **What it does** | Transcribe spoken audio to text in the speaker's original language. Multilingual — works across 140+ languages. |
| **Why on-device** | Complete privacy for voice input. Works offline. No cloud ASR costs. Essential for sensitive contexts (medical dictation, legal notes). |
| **Feasibility** | **Easy** — Officially demonstrated with working code. FLEURS benchmark: 0.08 word error rate for E4B. |
| **Prompt example** | `"Transcribe the following speech segment in its original language."` |
| **Limitations** | Max 30-second audio clips. 25 tokens/second of audio. Background noise affects quality. Single-speaker optimized. |

### 2.2 Speech Translation

| Aspect | Detail |
|--------|--------|
| **What it does** | Translate spoken audio directly from one language to another — e.g., spoken Japanese → English text. Combines ASR + translation in one step. |
| **Why on-device** | Real-time travel translator without internet. Privacy for business conversations. |
| **Feasibility** | **Easy** — Officially demonstrated (English → Korean, multilingual examples). CoVoST benchmark: 35.54 BLEU for E4B. |
| **Prompt example** | `"Transcribe the following speech in English, then translate it into Spanish."` |
| **Limitations** | Same 30-second limit. Translation quality varies by language pair. No streaming transcription — batch only. |

### 2.3 Audio Journaling & Summarization

| Aspect | Detail |
|--------|--------|
| **What it does** | Process multiple audio clips (voice memos, journal entries) and generate summaries, extract themes, track mood/sentiment. |
| **Why on-device** | Deeply personal data stays private. Demonstrated in official docs with 5 journal entries → concise overview. |
| **Feasibility** | **Easy** — Officially demonstrated with multi-audio input. Combine ASR + text reasoning. |
| **Prompt example** | `"Give me a concise overview of these audio files."` (with multiple audio clips attached) |
| **Limitations** | Each clip max 30 seconds. Total audio tokens consume context window. |

### 2.4 Voice-Driven Commands & Smart Replies

| Aspect | Detail |
|--------|--------|
| **What it does** | Accept voice input, understand intent, and generate contextual responses or actions. Enables voice-first app interfaces. |
| **Why on-device** | Zero-latency voice interaction. No cloud round-trip. Works in airplane mode, underground, rural areas. |
| **Feasibility** | **Medium** — Combine audio input with function calling for voice-triggered actions. Requires building the integration pipeline. |
| **Prompt example** | Audio input → `"What did the user ask for? Respond with the appropriate action."` |
| **Limitations** | Not real-time streaming. Batch transcription introduces latency. No text-to-speech output (Gemma 4 is text-output only). |

---

## 3. Text & Language Use Cases

### 3.1 On-Device Chat Assistant

| Aspect | Detail |
|--------|--------|
| **What it does** | General-purpose conversational AI running entirely on-device. Multi-turn chat with system prompt customization, 128K context window. |
| **Why on-device** | Total privacy — no conversation data leaves the device. Works offline. Custom personality/behavior via system prompts. |
| **Feasibility** | **Easy** — Core capability. LiteRT-LM Kotlin API supports multi-turn conversations natively. Google AI Edge Gallery already demonstrates this. |
| **Limitations** | 52 tokens/sec decode on GPU means ~2-4 second response time for typical answers. Knowledge cutoff January 2025. |

### 3.2 Text Summarization

| Aspect | Detail |
|--------|--------|
| **What it does** | Summarize articles, emails, documents, meeting notes into concise bullet points or paragraphs. 128K context handles long documents. |
| **Why on-device** | Summarize confidential business documents without cloud exposure. Works on flights. |
| **Feasibility** | **Easy** — Standard LLM capability. 128K context window handles substantial documents. Model card lists "Text Summarization" as intended use. |
| **Limitations** | Very long documents may need chunking strategies. Summary quality varies with domain specificity. |

### 3.3 Translation (Text-to-Text)

| Aspect | Detail |
|--------|--------|
| **What it does** | Translate text between 140+ languages with cultural context understanding. Goes beyond word-for-word translation. |
| **Why on-device** | Offline translation for travelers. Privacy for sensitive content (legal, medical documents). |
| **Feasibility** | **Easy** — Natively trained on 140+ languages. MMMLU multilingual benchmark: 60% for E2B. |
| **Limitations** | Quality varies for low-resource languages. No domain-specific translation fine-tuning out of the box. |

### 3.4 Code Generation & Assistance

| Aspect | Detail |
|--------|--------|
| **What it does** | Generate code, complete functions, fix bugs, explain code, convert between languages. LiveCodeBench v6: 29.1% for E2B. |
| **Why on-device** | Offline coding assistant. No proprietary code sent to cloud. Blog highlights "offline code assistant" as a use case. |
| **Feasibility** | **Medium** — E2B is the smallest model; coding benchmarks are modest (29.1% LiveCodeBench). Better suited as a helper than a primary code agent. |
| **Limitations** | E2B significantly weaker at coding than larger models (31B: 80%). Best for simple snippets, not complex architecture. |

### 3.5 Smart Replies & Message Drafting

| Aspect | Detail |
|--------|--------|
| **What it does** | Generate contextual reply suggestions for messages, compose email drafts, rewrite text for tone/clarity. |
| **Why on-device** | Message content stays private. Low-latency suggestions that feel native. |
| **Feasibility** | **Easy** — Simple text generation task. Can be constrained to short outputs for fast inference. |
| **Limitations** | Suggestions may not match user's personal style without fine-tuning. |

---

## 4. Multimodal Use Cases (Camera + Text + Audio)

### 4.1 Accessibility — Scene Narration for Visually Impaired

| Aspect | Detail |
|--------|--------|
| **What it does** | Point phone camera at surroundings → get spoken description of the scene, obstacles, text on signs, people, etc. Combines vision + text generation (+ TTS for speech output). |
| **Why on-device** | Critical for privacy (continuous camera feed). Must work offline. Low-latency is essential for navigation safety. |
| **Feasibility** | **Medium** — Vision understanding is strong. Requires continuous camera frame processing pipeline + external TTS engine (Gemma 4 outputs text only). |
| **Prompt example** | `"Describe what you see in detail, including any text, people, obstacles, and the general environment. Be concise and actionable."` |
| **Limitations** | 2-3 second latency per frame means narration is delayed. Not real-time enough for fast-moving environments without hybrid approach. |

### 4.2 Cooking Assistant — Ingredient Identification

| Aspect | Detail |
|--------|--------|
| **What it does** | Point camera at ingredients/dishes → identify items, suggest recipes, estimate nutritional info, check dietary compatibility. |
| **Why on-device** | Works in kitchen without touching greasy phone for internet search. Voice input for hands-free operation (E2B audio support). |
| **Feasibility** | **Easy-Medium** — Image classification + VQA + text generation. Combine: photo of ingredients → "What can I cook with these?" |
| **Prompt example** | `"Identify all the ingredients visible on this counter and suggest 3 quick recipes I can make with them."` |
| **Limitations** | May not identify uncommon/exotic ingredients accurately. Nutritional estimates are approximate. |

### 4.3 Plant & Animal Identification

| Aspect | Detail |
|--------|--------|
| **What it does** | Identify plants, animals, insects, mushrooms from photos. Provide species info, care instructions, toxicity warnings. |
| **Why on-device** | Works in wilderness/nature without connectivity. Hikers, gardeners, farmers can identify species instantly. |
| **Feasibility** | **Medium** — Visual understanding is good but accuracy depends on the model's training data for specific species. Open-vocabulary means it can attempt any species. |
| **Prompt example** | `"What plant is this? Is it safe to touch? What care does it need?"` |
| **Limitations** | Not a replacement for expert botanical/zoological identification. May misidentify similar-looking species. Should not be relied upon for mushroom edibility. |

### 4.4 Travel Assistant — Landmark & Sign Reading

| Aspect | Detail |
|--------|--------|
| **What it does** | Identify landmarks, read foreign-language signs, provide historical context, translate menus. Combines vision + OCR + translation + knowledge. |
| **Why on-device** | Essential offline capability for international travelers. No roaming data needed. Privacy for location data. |
| **Feasibility** | **Easy-Medium** — Combines proven capabilities (OCR, translation, VQA). Multilingual OCR officially demonstrated. |
| **Prompt example** | `"What does this sign say? Translate it to English and explain if there are any important warnings."` |
| **Limitations** | Knowledge cutoff means very new landmarks may not be recognized. Complex scripts may need high token budget. |

### 4.5 Homework & Study Helper

| Aspect | Detail |
|--------|--------|
| **What it does** | Photo of math problem, textbook page, or diagram → step-by-step explanation, answer, or study flashcards. Thinking mode enables showing reasoning. |
| **Why on-device** | Privacy for student data. Works during study sessions without internet. Google AI Edge Gallery demonstrates flashcard generation as an Agent Skill. |
| **Feasibility** | **Medium** — Math reasoning is decent (AIME 2026: 20.8% for E2B — modest). Better for algebra/geometry than competition math. Strong at reading textbooks and generating summaries. |
| **Prompt example** | `"Solve this math problem step by step."` (with thinking mode enabled) |
| **Limitations** | E2B is the weakest at advanced math among the Gemma 4 family. Best for K-12 level. |

### 4.6 Fashion & Style Advisor

| Aspect | Detail |
|--------|--------|
| **What it does** | Analyze outfit photos, suggest complementary items, identify styles, provide occasion-appropriate feedback. |
| **Why on-device** | Personal wardrobe photos stay private. Quick morning outfit check without internet. |
| **Feasibility** | **Easy-Medium** — Image understanding + text generation. No specialized fashion training, but general visual reasoning works for style advice. |
| **Prompt example** | `"Analyze this outfit. Is it appropriate for a business casual office? What would you change?"` |
| **Limitations** | Fashion advice is subjective. May not recognize specific brands or current trends beyond training cutoff. |

---

## 5. Enterprise & Productivity Use Cases

### 5.1 Document Analysis & Form Extraction

| Aspect | Detail |
|--------|--------|
| **What it does** | Extract structured data from business documents, forms, contracts — identify fields, values, tables. Model card explicitly lists "Document/PDF parsing" as a capability. |
| **Why on-device** | Regulatory compliance — sensitive documents never leave the device. HIPAA, GDPR-friendly processing. |
| **Feasibility** | **Medium** — Confirmed capability. OmniDocBench score (0.365 edit distance for E2B) is functional but not best-in-class. Larger models score significantly better. |
| **Prompt example** | `"Extract all form fields and their values from this document as structured JSON."` |
| **Limitations** | Multi-page documents need page-by-page processing. E2B accuracy is lower than 31B model for complex documents. |

### 5.2 Inventory & Asset Management

| Aspect | Detail |
|--------|--------|
| **What it does** | Photograph shelves/warehouses → identify products, count items, detect low stock, read barcodes/labels. Combines object detection + OCR + counting. |
| **Why on-device** | Works in warehouses without WiFi. Fast scan-and-identify workflow. No cloud dependency for operations. |
| **Feasibility** | **Medium-Hard** — Combines multiple capabilities. Counting accuracy is limited for large quantities. Barcode reading is not a trained specialty. |
| **Prompt example** | `"Identify all products visible on this shelf. Count the items in each row. Flag any empty spaces."` |
| **Limitations** | Counting accuracy degrades significantly beyond ~10 items. No barcode decoding — can read text labels only. |

### 5.3 Quality Inspection (Manufacturing/Agriculture)

| Aspect | Detail |
|--------|--------|
| **What it does** | Inspect products, crops, or infrastructure for defects — scratches, cracks, discoloration, pest damage. |
| **Why on-device** | Works on factory floors and farms without connectivity. Real-time feedback during inspection rounds. |
| **Feasibility** | **Hard** — General visual understanding may catch obvious defects, but lacks the specialized training of purpose-built defect detection models. Would benefit greatly from fine-tuning on domain-specific data. |
| **Prompt example** | `"Inspect this product image. Identify any visible defects, scratches, dents, or quality issues."` |
| **Limitations** | Not trained on industrial defect datasets. Fine-tuning required for production accuracy. E2B may miss subtle defects. |

### 5.4 Meeting Notes & Productivity

| Aspect | Detail |
|--------|--------|
| **What it does** | Transcribe voice memos (audio input), summarize meeting notes, extract action items, draft follow-up emails. |
| **Why on-device** | Confidential meeting content stays on-device. Works in signal-dead conference rooms. |
| **Feasibility** | **Medium** — Combines audio ASR + text summarization. 30-second audio limit means meetings need to be chunked into segments. |
| **Prompt example** | Audio clip + `"Summarize this meeting segment. List action items with assigned owners."` |
| **Limitations** | 30-second audio cap is restrictive for meetings. Would need a segmentation pipeline. |

---

## 6. Agentic & Creative Use Cases

### 6.1 On-Device Autonomous Agents (Agent Skills)

| Aspect | Detail |
|--------|--------|
| **What it does** | Multi-step, autonomous workflows running entirely on-device — plan, execute tools, navigate apps, complete complex tasks. Google AI Edge Gallery showcases this with "Agent Skills." |
| **Why on-device** | Privacy-preserving agents that don't leak user data. Work offline. Native function calling support for structured tool use. |
| **Feasibility** | **Medium** — Officially demonstrated and promoted. Agent Skills in Google AI Edge Gallery show: Wikipedia querying, data visualization, music synthesis integration, and multi-step workflows. τ2-bench agentic score: 6.6% (E2B) — modest for complex reasoning chains. |
| **Showcase examples** | Build a skill to query Wikipedia; Create flashcards from video summaries; Pair photos with mood-matching music; Build a working animal vocal call app entirely through conversation. |
| **Limitations** | E2B has limited agentic capability vs larger models (31B: 86.4% on τ2-bench retail). Multi-step planning accuracy is lower. Best for 2-3 step workflows. |

### 6.2 Knowledge Augmentation & Retrieval

| Aspect | Detail |
|--------|--------|
| **What it does** | On-device RAG-style workflows — agent queries local knowledge bases, documents, or APIs to answer questions beyond its training data. |
| **Why on-device** | Google AI Edge Gallery demonstrates "agentic enrichment" — querying Wikipedia or other sources via agent skills to extend model knowledge. |
| **Feasibility** | **Medium** — Requires building retrieval pipeline + function calling integration. The 128K context window helps process retrieved documents. |
| **Limitations** | Retrieval quality depends on the pipeline. E2B's reasoning capability limits complex multi-hop queries. |

### 6.3 Interactive Content Creation

| Aspect | Detail |
|--------|--------|
| **What it does** | Transform content into interactive formats — flashcards from text/video, data visualizations from spoken input, study guides from textbook photos. |
| **Why on-device** | Google AI Edge Gallery demonstrates: "Transform paragraphs or videos into concise summaries or flashcards for studying" and "transform data into interactive visualizations or graphs." |
| **Feasibility** | **Medium** — Demonstrated in Google AI Edge Gallery. Requires UI integration to render generated structured output (HTML, markdown, chart specs). |
| **Limitations** | Cannot generate images or render visuals itself — outputs text/markup that the app must render. |

### 6.4 Art & Style Identification

| Aspect | Detail |
|--------|--------|
| **What it does** | Identify art styles, artists, historical periods from paintings/sculptures. Describe artistic techniques, color palettes, composition. |
| **Why on-device** | Museum/gallery companion without internet. Educational tool for art students. |
| **Feasibility** | **Easy** — General image understanding + knowledge base. Works well for well-known artworks and common styles. |
| **Prompt example** | `"What art style is this painting? Who might have created it? Describe the techniques used."` |
| **Limitations** | May not identify obscure or contemporary artists. Knowledge cutoff affects recent artworks. |

### 6.5 Photo Editing Suggestions & Metadata

| Aspect | Detail |
|--------|--------|
| **What it does** | Analyze photos and suggest improvements — cropping, exposure, composition. Generate metadata tags, alt text, and descriptions. |
| **Why on-device** | Fast photo curation without cloud upload. Accessibility: auto-generate alt text for visually impaired users. |
| **Feasibility** | **Easy** — Image analysis + text generation. Cannot edit photos itself, but can provide actionable suggestions. |
| **Prompt example** | `"Analyze this photo's composition, lighting, and color. Suggest improvements and generate 5 relevant tags."` |
| **Limitations** | Cannot apply edits — text output only. Suggestions are general, not professional photography-grade. |

### 6.6 Multimodal Integration Hub

| Aspect | Detail |
|--------|--------|
| **What it does** | Orchestrate other on-device models — pair with text-to-speech for voice output, image generation models, or music synthesis. Google AI Edge Gallery demonstrates "integrate with other models, such as text-to-speech, image generation, or music synthesis." |
| **Why on-device** | Creates comprehensive end-to-end experiences entirely on-device. Gemma 4 acts as the "brain" coordinating specialized models. |
| **Feasibility** | **Hard** — Requires building multi-model pipeline. Each additional model adds memory and latency. |
| **Limitations** | Memory constraints on mobile — running multiple models simultaneously is challenging. Orchestration adds complexity. |

---

## 7. Video Use Cases

### 7.1 Video Understanding & Summarization

| Aspect | Detail |
|--------|--------|
| **What it does** | Process video as frame sequences (up to 60 seconds at 1fps). Summarize video content, answer questions about what happened, identify key moments. |
| **Why on-device** | Privacy for personal/security videos. Works offline for surveillance review. |
| **Feasibility** | **Medium** — Officially supported. Use low token budget per frame (70-140) to process more frames within context. |
| **Limitations** | Max 60 seconds at 1fps. Uses significant context window. Not suitable for long-form video. |

---

## Feasibility Summary Matrix

| Use Case | Feasibility | Privacy Benefit | Offline Value | Latency |
|----------|------------|-----------------|---------------|---------|
| Image Captioning | Easy | High | High | ~2-3s |
| Visual Q&A | Easy | High | High | ~2-3s |
| OCR (Multilingual) | Easy | Very High | Very High | ~2-4s |
| Document/Receipt Scanning | Medium | Very High | High | ~3-5s |
| Chart Understanding | Medium | High | Medium | ~3-5s |
| Image Classification | Easy | Medium | High | ~1-2s |
| Screen/UI Understanding | Medium | Very High | Medium | ~3-5s |
| Speech Recognition (ASR) | Easy | Very High | Very High | ~2-4s |
| Speech Translation | Easy | Very High | Very High | ~3-5s |
| On-Device Chat | Easy | Very High | Very High | ~2-4s |
| Text Summarization | Easy | High | High | ~3-6s |
| Translation (Text) | Easy | High | Very High | ~2-4s |
| Scene Narration (A11y) | Medium | Very High | Very High | ~3-5s |
| Cooking Assistant | Easy-Medium | Medium | High | ~3-5s |
| Plant/Animal ID | Medium | Low | Very High | ~2-4s |
| Travel Assistant | Easy-Medium | Medium | Very High | ~3-5s |
| Agent Skills | Medium | Very High | High | Variable |
| Document Analysis | Medium | Very High | High | ~3-6s |
| Quality Inspection | Hard | High | High | ~3-5s |
| Code Assistance | Medium | High | High | ~3-8s |
| Video Understanding | Medium | High | High | ~5-15s |

---

## Recommended Next Steps for Our Project

1. **Phase 1 (Current):** Object detection POC with Gemma 4 E2B
2. **Phase 2 (Quick wins):** Add image captioning, VQA, and OCR as additional features — these are trivially easy to add since they use the same model and pipeline, just different prompts
3. **Phase 3 (High value):** On-device speech recognition (ASR) for voice-driven interaction — officially supported on E2B, adds hands-free capability
4. **Phase 4 (Differentiation):** Accessibility features — scene narration for visually impaired users combines vision + text and has high social impact
5. **Phase 5 (Advanced):** Agent Skills integration — agentic workflows with function calling for complex multi-step tasks

The key insight is that **one model, one download, one integration** unlocks all of these capabilities. The 2.58 GB E2B model is already on-device for object detection — every additional use case is just a different prompt.

---

## Sources

- [Gemma 4 Model Card](https://ai.google.dev/gemma/docs/core/model_card_4) — Core capabilities, benchmarks, intended usage
- [Image Understanding Guide](https://ai.google.dev/gemma/docs/capabilities/vision/image) — VQA, OCR, object detection, variable resolution
- [Audio Understanding Guide](https://ai.google.dev/gemma/docs/capabilities/audio) — ASR, speech translation, audio journaling
- [Function Calling Guide](https://ai.google.dev/gemma/docs/capabilities/text/function-calling-gemma4) — Tool use, agentic workflows
- [Thinking Mode Guide](https://ai.google.dev/gemma/docs/capabilities/thinking) — Step-by-step reasoning
- [Google AI Edge Gallery Blog](https://developers.googleblog.com/en/bring-state-of-the-art-agentic-skills-to-the-edge-with-gemma-4/) — Agent Skills, on-device agentic use cases
- [Gemma 4 Launch Blog](https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/) — Overview, ecosystem, capabilities
- [Gemma 4 Landing Page](https://deepmind.google/models/gemma/gemma-4/) — Model sizes, performance, agentic workflows
