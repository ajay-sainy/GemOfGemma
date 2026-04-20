# Gemma 4 — Object Detection Capabilities

## Can Gemma 4 Do Object Detection?

**YES.** Gemma 4 natively supports object detection with bounding box output. This is confirmed in the official documentation and demonstrated with working code examples.

The model card explicitly lists under "Core Capabilities → Image Understanding":
> "Object detection, Document/PDF parsing, screen and UI understanding, chart comprehension, OCR (including multilingual), handwriting recognition, and pointing."

---

## How Object Detection Works

Gemma 4 is a **vision-language model (VLM)**, not a traditional object detector like YOLO or SSD. It performs detection through **prompted visual reasoning**:

1. You provide an image and a text prompt (e.g., "detect person and cat")
2. The model outputs a JSON response with bounding box coordinates
3. Coordinates are normalized to a **1000×1000 grid**
4. You rescale coordinates to original image dimensions

### Output Format

```json
[
  {"box_2d": [244, 256, 948, 405], "label": "person"},
  {"box_2d": [357, 606, 655, 803], "label": "cat"}
]
```

Where `box_2d` is `[y1, x1, y2, x2]` — normalized to 1000×1000.

### Coordinate Rescaling

```python
y1, x1, y2, x2 = [int(coord) / 1000 for coord in coordinates]
y1 = round(y1 * image_height)
x1 = round(x1 * image_width)
y2 = round(y2 * image_height)
x2 = round(x2 * image_width)
```

### Kotlin/Android Equivalent

```kotlin
fun rescaleBoundingBox(box: List<Int>, imageWidth: Int, imageHeight: Int): Rect {
    val y1 = (box[0] / 1000.0 * imageHeight).roundToInt()
    val x1 = (box[1] / 1000.0 * imageWidth).roundToInt()
    val y2 = (box[2] / 1000.0 * imageHeight).roundToInt()
    val x2 = (box[3] / 1000.0 * imageWidth).roundToInt()
    return Rect(x1, y1, x2, y2)
}
```

---

## Prompting for Object Detection

### Basic Detection
```
detect person and cat
```

### Structured Output
```
detect person and car, output only ```json
```

### Open-Ended Detection
```
Detect all objects in this image and return their bounding boxes.
```

---

## Variable Resolution (Token Budget)

Gemma 4 supports variable image resolution via a **token budget** that controls how many visual tokens represent an image:

| Token Budget | Visual Tokens | Best For |
|-------------|---------------|----------|
| 70 | ~64 | Fast inference, classification |
| 140 | ~121 | Basic detection |
| 280 | ~256 | Good detection accuracy |
| 560 | ~529 | High-accuracy detection |
| 1120 | ~1000+ | Maximum detail (OCR, fine text) |

**For object detection, higher budgets yield better results.** The official docs demonstrate that budget 70 misses objects, while 560 catches significantly more.

In code (Python/Transformers):
```python
vqa_pipe.image_processor.max_soft_tokens = 560  # Higher = better detection
```

For LiteRT-LM on Android, the token budget may be configurable via engine options.

---

## Comparison: VLM Detection vs Traditional Detection

| Aspect | Gemma 4 (VLM) | YOLO / SSD (Traditional) |
|--------|---------------|--------------------------|
| Architecture | Generative language model | Dedicated detector |
| Output | JSON text with bboxes | Tensor of bboxes |
| Latency | Higher (~100-500ms+ per frame) | Lower (~10-50ms per frame) |
| Open-vocabulary | ✅ Detect ANY object by name | ❌ Fixed class set |
| Training | Pre-trained, zero-shot | Needs training on classes |
| Accuracy | Good, improves with token budget | Very high for trained classes |
| Real-time video | Challenging | ✅ Designed for it |
| Flexibility | Can describe, count, reason | Only detects + classifies |
| Post-processing | Parse JSON | NMS on tensors |

### Key Tradeoffs for Our Project

**Advantages of Gemma 4 for Object Detection:**
- **Open-vocabulary**: Can detect ANY object class without retraining
- **Rich context**: Can describe detected objects, their relationships, scene context
- **No class limit**: Not restricted to a fixed set of 80 COCO classes
- **Multi-task**: Same model for detection, captioning, OCR, reasoning
- **Reasoning**: Can answer questions about detected objects

**Disadvantages:**
- **Latency**: Not suitable for real-time 30fps video detection
- **Output parsing**: Need to parse JSON text vs structured tensors
- **Consistency**: Generative output can vary between identical prompts
- **No confidence scores**: Unlike YOLO, no probability per detection
- **Model size**: 2.58 GB vs ~30 MB for a typical YOLO model

---

## Recommended Approach for Our App

### Option A: Gemma 4 as Primary Detector (Simpler)
- Use Gemma 4 E2B for all detection
- Capture frames, send to model, parse JSON response
- Best for: photo analysis, single-shot detection, detailed understanding
- Frame rate: ~1-2 FPS (sufficient for many use cases)

### Option B: Hybrid Pipeline (Better for Real-Time)
- Use a lightweight traditional detector (e.g., MediaPipe object detection) for real-time bounding boxes
- Use Gemma 4 for detailed analysis, identification of unknown objects, scene understanding
- Best for: real-time video with intelligent overlay

### Option C: Gemma 4 Vision + Custom Post-Processing
- Use Gemma 4's vision to analyze camera frames at lower frequency
- Implement tracking/interpolation between Gemma 4 inferences
- Provide rich, contextual detection results

### Recommendation
**Start with Option A** (Gemma 4 as primary detector) for the POC. It's the simplest path, demonstrates Gemma 4's capabilities directly, and the latency may be acceptable for a demo. If real-time performance is needed, evolve to Option B.

---

## Additional Vision Capabilities

Beyond object detection, Gemma 4 can also do:
- **Image captioning / description**
- **OCR** (including multilingual — Japanese, Chinese, etc.)
- **Document / PDF parsing**
- **Chart comprehension**
- **Screen / UI understanding**
- **Handwriting recognition**
- **Visual question answering**
- **Multi-image comparison**
- **Video analysis** (sequence of frames, up to 60s at 1fps)

These capabilities could enhance our app beyond basic detection.
