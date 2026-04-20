package com.gemofgemma.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService implementation for GemOfGemma.
 * Enables UI automation — reading screen content, tapping elements,
 * typing text, and performing global actions (home, back, recents).
 *
 * Must be enabled manually by the user in Settings > Accessibility.
 * Access the running instance via [instance] companion property.
 */
class GemOfGemmaAccessibilityService : AccessibilityService(), AccessibilityBridge {

    companion object {
        private const val TAG = "GemOfGemmaA11y"

        @Volatile
        var instance: GemOfGemmaAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are processed on-demand via AccessibilityBridge methods,
        // not continuously through the event stream.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // --- AccessibilityBridge implementation ---

    override fun isServiceEnabled(): Boolean = instance != null

    override fun executeGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }

    override fun findAndClickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            val target = nodes.firstOrNull() ?: return false
            if (target.isClickable) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                // Walk up to find a clickable parent
                findClickableParent(target)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ?: false
            }
        } finally {
            rootNode.recycle()
        }
    }

    override fun findAndClickById(viewId: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            nodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } finally {
            rootNode.recycle()
        }
    }

    override fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: return false
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            rootNode.recycle()
        }
    }

    override fun getScreenContent(): List<NodeInfo> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<NodeInfo>()
        try {
            traverseTree(rootNode, nodes)
        } finally {
            rootNode.recycle()
        }
        return nodes
    }

    // --- Private helpers ---

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun traverseTree(node: AccessibilityNodeInfo, results: MutableList<NodeInfo>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (node.text != null || node.contentDescription != null || node.isClickable) {
            results.add(
                NodeInfo(
                    className = node.className?.toString() ?: "",
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    viewId = node.viewIdResourceName,
                    isClickable = node.isClickable,
                    bounds = bounds
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseTree(child, results)
        }
    }
}
