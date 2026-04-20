package com.gemofgemma.ui.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.gemofgemma.core.model.DetectionResult
import com.gemofgemma.ui.theme.DetectionBlue
import com.gemofgemma.ui.theme.DetectionCyan
import com.gemofgemma.ui.theme.DetectionGreen
import com.gemofgemma.ui.theme.DetectionOrange
import com.gemofgemma.ui.theme.DetectionPink
import com.gemofgemma.ui.theme.DetectionPurple
import com.gemofgemma.ui.theme.DetectionTeal
import com.gemofgemma.ui.theme.DetectionYellow
import kotlinx.coroutines.delay

private val PEOPLE_KEYWORDS = setOf(
    "person", "man", "woman", "child", "people", "face", "human", "boy", "girl", "baby"
)
private val VEHICLE_KEYWORDS = setOf(
    "car", "truck", "bus", "motorcycle", "bicycle", "boat", "airplane", "train", "vehicle",
    "scooter", "van", "taxi"
)
private val FOOD_KEYWORDS = setOf(
    "food", "apple", "banana", "pizza", "cake", "sandwich", "fruit", "vegetable", "bread",
    "donut", "hot dog", "orange", "broccoli", "carrot"
)
private val ANIMAL_KEYWORDS = setOf(
    "cat", "dog", "bird", "horse", "animal", "fish", "sheep", "cow", "bear", "zebra",
    "giraffe", "elephant"
)
private val ELECTRONICS_KEYWORDS = setOf(
    "laptop", "phone", "tv", "monitor", "keyboard", "mouse", "computer", "tablet", "remote",
    "cell phone", "microwave", "oven", "toaster"
)
private val FURNITURE_KEYWORDS = setOf(
    "chair", "table", "couch", "bed", "desk", "sofa", "bench", "dining table", "potted plant"
)

fun categoryColorForLabel(label: String): Color {
    val lower = label.lowercase().trim()
    return when {
        PEOPLE_KEYWORDS.any { lower.contains(it) } -> DetectionBlue
        VEHICLE_KEYWORDS.any { lower.contains(it) } -> DetectionGreen
        FOOD_KEYWORDS.any { lower.contains(it) } -> DetectionOrange
        ANIMAL_KEYWORDS.any { lower.contains(it) } -> DetectionPink
        ELECTRONICS_KEYWORDS.any { lower.contains(it) } -> DetectionTeal
        FURNITURE_KEYWORDS.any { lower.contains(it) } -> DetectionYellow
        else -> DetectionPurple
    }
}

@Composable
fun BoundingBoxOverlay(
    detections: List<DetectionResult>,
    imageWidth: Int,
    imageHeight: Int,
    selectedIndex: Int = -1,
    onDetectionTapped: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Per-box staggered spring animations
    val animatables = remember(detections) {
        detections.map { Animatable(0f) }
    }

    LaunchedEffect(detections) {
        animatables.forEach { animatable ->
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Track laid-out bounding rects for tap detection
    val boxRects = remember(detections) { mutableListOf<Rect>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(detections) {
                detectTapGestures { offset ->
                    // Find tapped detection (reverse order so topmost matches first)
                    val tappedIndex = boxRects
                        .indexOfLast { rect -> rect.contains(offset) }
                    if (tappedIndex >= 0) {
                        onDetectionTapped(tappedIndex)
                    }
                }
            }
    ) {
        // Simple direct scaling — Gemma coordinates are 0-1000
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        boxRects.clear()

        detections.forEachIndexed { index, detection ->
            val progress = if (index < animatables.size) animatables[index].value else 1f
            val color = categoryColorForLabel(detection.label)
            val isSelected = index == selectedIndex

            // Direct coordinate mapping
            val left = detection.x1 * scaleX
            val top = detection.y1 * scaleY
            val w = (detection.x2 - detection.x1) * scaleX * progress
            val h = (detection.y2 - detection.y1) * scaleY * progress

            boxRects.add(Rect(Offset(left, top), Size(w, h)))

            // Selection glow
            if (isSelected) {
                drawRoundRect(
                    color = color.copy(alpha = 0.15f),
                    topLeft = Offset(left - 4f, top - 4f),
                    size = Size(w + 8f, h + 8f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
            }

            // Corner brackets style (Google Lens inspired)
            val cornerLen = minOf(w, h) * 0.2f
            val strokeW = if (isSelected) 4f else 3f
            drawCornerBrackets(
                color = color,
                left = left,
                top = top,
                width = w,
                height = h,
                cornerLength = cornerLen,
                strokeWidth = strokeW
            )

            // Confidence badge + label
            val confidencePct = if (detection.confidence > 0f) {
                " ${(detection.confidence * 100).toInt()}%"
            } else ""
            val labelText = "${detection.label}$confidencePct"

            val textLayout = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    fontSize = if (isSelected) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )

            val padH = 10f
            val padV = 5f
            val badgeW = textLayout.size.width + padH * 2
            val badgeH = textLayout.size.height + padV * 2

            // Badge background
            drawRoundRect(
                color = color.copy(alpha = if (isSelected) 0.95f else 0.85f),
                topLeft = Offset(left, top - badgeH - 6f),
                size = Size(badgeW, badgeH),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // Badge text
            drawText(
                textMeasurer = textMeasurer,
                text = labelText,
                topLeft = Offset(left + padH, top - badgeH - 6f + padV),
                style = TextStyle(
                    fontSize = if (isSelected) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
        }
    }
}

private fun DrawScope.drawCornerBrackets(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cornerLength: Float,
    strokeWidth: Float
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val right = left + width
    val bottom = top + height
    val cl = cornerLength.coerceAtLeast(12f)

    // Top-left
    val tlPath = Path().apply {
        moveTo(left, top + cl)
        lineTo(left, top)
        lineTo(left + cl, top)
    }
    drawPath(tlPath, color, style = stroke)

    // Top-right
    val trPath = Path().apply {
        moveTo(right - cl, top)
        lineTo(right, top)
        lineTo(right, top + cl)
    }
    drawPath(trPath, color, style = stroke)

    // Bottom-left
    val blPath = Path().apply {
        moveTo(left, bottom - cl)
        lineTo(left, bottom)
        lineTo(left + cl, bottom)
    }
    drawPath(blPath, color, style = stroke)

    // Bottom-right
    val brPath = Path().apply {
        moveTo(right - cl, bottom)
        lineTo(right, bottom)
        lineTo(right, bottom - cl)
    }
    drawPath(brPath, color, style = stroke)
}
