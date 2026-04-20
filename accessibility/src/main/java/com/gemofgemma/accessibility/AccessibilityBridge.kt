package com.gemofgemma.accessibility

import android.graphics.Rect

/**
 * Interface for the action layer to request accessibility-based UI automation.
 * Implemented by [GemOfGemmaAccessibilityService].
 *
 * Callers should check [isServiceEnabled] before attempting any operations.
 * Access the live instance via [GemOfGemmaAccessibilityService.instance].
 */
interface AccessibilityBridge {
    fun isServiceEnabled(): Boolean
    fun executeGlobalAction(action: Int): Boolean
    fun findAndClickByText(text: String): Boolean
    fun findAndClickById(viewId: String): Boolean
    fun typeText(text: String): Boolean
    fun getScreenContent(): List<NodeInfo>
}

data class NodeInfo(
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val isClickable: Boolean,
    val bounds: Rect?
)
