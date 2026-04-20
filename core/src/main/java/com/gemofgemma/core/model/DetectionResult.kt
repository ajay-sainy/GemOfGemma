package com.gemofgemma.core.model

import android.graphics.RectF
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class DetectionResult(
    val label: String,
    val y1: Float,
    val x1: Float,
    val y2: Float,
    val x2: Float,
    val confidence: Float = 0f
) {
    @Transient
    val boundingBox: RectF = RectF(x1, y1, x2, y2)

    fun rescale(imageWidth: Int, imageHeight: Int): DetectionResult {
        return copy(
            x1 = x1 / 1000f * imageWidth,
            y1 = y1 / 1000f * imageHeight,
            x2 = x2 / 1000f * imageWidth,
            y2 = y2 / 1000f * imageHeight,
            confidence = confidence
        )
    }
}
