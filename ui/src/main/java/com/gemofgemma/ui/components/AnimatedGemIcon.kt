package com.gemofgemma.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gemofgemma.ui.theme.GradientEnd
import com.gemofgemma.ui.theme.GradientStart
import com.gemofgemma.ui.theme.GradientWarm
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedGemIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gem")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val radius = (this.size.minDimension / 2f) * pulse

        rotate(rotation, pivot = Offset(cx, cy)) {
            val path = Path()
            val sides = 6
            for (i in 0 until sides) {
                val angle = Math.toRadians((60.0 * i) - 90.0)
                val x = cx + radius * cos(angle).toFloat()
                val y = cy + radius * sin(angle).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(GradientStart, GradientEnd, GradientWarm),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height)
                )
            )

            // Inner highlight facet
            val innerPath = Path()
            val innerRadius = radius * 0.5f
            for (i in 0 until sides) {
                val angle = Math.toRadians((60.0 * i) - 90.0)
                val x = cx + innerRadius * cos(angle).toFloat()
                val y = cy + innerRadius * sin(angle).toFloat()
                if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
            }
            innerPath.close()
            drawPath(
                path = innerPath,
                color = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}
