package com.gemofgemma.core.model

/**
 * Represents a single emission during streaming AI response.
 * Carries both the accumulated main response and any thinking/reasoning text.
 * When a native tool call is detected after streaming, [actionResult] is set
 * on the final emitted chunk.
 */
data class StreamChunk(
    val responseText: String,
    val thinkingText: String? = null,
    val actionResult: ActionResult? = null
)
