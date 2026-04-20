package com.gemofgemma.actions.model

/**
 * Represents a parsed function/tool call from the AI model's output.
 * Created from LiteRT-LM native tool calls and consumed by
 * [com.gemofgemma.actions.ActionDispatcher].
 */
data class ParsedAction(
    val functionName: String,
    val parameters: Map<String, Any>,
    val rawOutput: String = ""
)
