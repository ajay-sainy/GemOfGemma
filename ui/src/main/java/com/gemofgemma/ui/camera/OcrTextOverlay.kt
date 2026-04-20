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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.gemofgemma.core.model.OcrTextBlock
import kotlinx.coroutines.delay

// Google Lens–style cyan highlight colors
private val OcrHighlight = Color(0xFF00BCD4)
private val OcrHighlightSelected = Color(0xFF0097A7)
private val OcrFillDefault = Color(0x3300BCD4)     // ~20% alpha cyan
private val OcrFillSelected = Color(0x7700BCD4)    // ~47% alpha cyan
private val OcrTextShadow = Color(0xCC000000)

@Composable
fun OcrTextOverlay(
    blocks: List<OcrTextBlock>,
    imageWidth: Int,
    imageHeight: Int,
    selectedIndices: Set<Int>,
    onBlockTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Staggered spring-in animations per block
    val animatables = remember(blocks) {
        blocks.map { Animatable(0f) }
    }

    // All boxes appear at once — no stagger delay
    LaunchedEffect(blocks) {
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

    val boxRects = remember(blocks) { mutableListOf<Rect>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(blocks) {
                detectTapGestures { offset ->
                    val tappedIndex = boxRects.indexOfLast { rect -> rect.contains(offset) }
                    if (tappedIndex >= 0) {
                        onBlockTapped(tappedIndex)
                    }
                }
            }
    ) {
        // Simple direct scaling — Gemma coordinates are 0-1000
        // ContentScale.Crop: image fills canvas, may be cropped
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        boxRects.clear()

        blocks.forEachIndexed { index, block ->
            val progress = if (index < animatables.size) animatables[index].value else 1f
            val isSelected = index in selectedIndices

            // Direct coordinate mapping (Gemma returns y1,x1,y2,x2 in 0-1000 range)
            val left = block.x1 * scaleX
            val top = block.y1 * scaleY
            val w = (block.x2 - block.x1) * scaleX * progress
            val h = (block.y2 - block.y1) * scaleY * progress

            boxRects.add(Rect(Offset(left, top), Size(w, h)))

            // Filled highlight rectangle
            val fillColor = if (isSelected) OcrFillSelected else OcrFillDefault
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Border stroke
            val strokeColor = if (isSelected) OcrHighlightSelected else OcrHighlight
            drawRoundRect(
                color = strokeColor.copy(alpha = 0.8f * progress),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = if (isSelected) 3f else 1.5f)
            )

            // Render the recognized text on top of the highlight
            if (h > 10f && w > 10f) {
                val fontSize = (h * 0.6f).coerceIn(8f, 20f)
                val textLayout = textMeasurer.measure(
                    text = block.text,
                    style = TextStyle(
                        fontSize = fontSize.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = w.toInt().coerceAtLeast(1)
                    )
                )

                // Text shadow for readability
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(left + 3f, top + (h - textLayout.size.height) / 2f + 1f),
                    color = OcrTextShadow
                )

                // Actual text
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(left + 2f, top + (h - textLayout.size.height) / 2f)
                )
            }
        }
    }
}
