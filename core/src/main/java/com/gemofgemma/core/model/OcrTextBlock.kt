package com.gemofgemma.core.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrTextBlock(
    val text: String,
    val y1: Float,
    val x1: Float,
    val y2: Float,
    val x2: Float
)
