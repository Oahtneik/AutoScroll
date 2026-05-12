package com.autoscroll.app.core

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Tree-traversal helpers over [AccessibilityNodeInfo].
 *
 * These walk the node tree depth-first looking for matches. Performance
 * matters — `TYPE_WINDOW_CONTENT_CHANGED` fires hundreds of times per second
 * during video playback, so callers should already throttle BEFORE invoking
 * these. See [AutoScrollEngine] for the throttle.
 *
 * On node lifecycle: from API 33 `recycle()` is deprecated and the system
 * pools nodes for us. We rely on that and don't manually recycle. Our minSdk
 * is 26, so on API 26-32 there is some pressure, but it's negligible for our
 * usage pattern (read once, drop reference).
 */

/**
 * Depth-first search returning the first node that matches [selector],
 * or null if none.
 */
fun AccessibilityNodeInfo?.findFirst(selector: NodeSelector): AccessibilityNodeInfo? {
    if (this == null) return null
    if (matches(selector)) return this
    for (i in 0 until childCount) {
        val child = getChild(i) ?: continue
        val hit = child.findFirst(selector)
        if (hit != null) return hit
    }
    return null
}

/**
 * Returns true if any node in the tree matches [selector]. Cheaper than
 * [findFirst] if you don't need the node itself.
 */
fun AccessibilityNodeInfo?.containsMatching(selector: NodeSelector): Boolean =
    findFirst(selector) != null

/**
 * Single-node match check.
 */
fun AccessibilityNodeInfo.matches(selector: NodeSelector): Boolean = when (selector) {
    is NodeSelector.ByResourceId ->
        viewIdResourceName == selector.id

    is NodeSelector.ByText ->
        text?.toString().equals(selector.text, ignoreCase = selector.ignoreCase)

    is NodeSelector.ByTextContains ->
        text?.toString()?.contains(selector.text, ignoreCase = selector.ignoreCase) == true

    is NodeSelector.ByContentDescContains ->
        contentDescription?.toString()?.contains(selector.text, ignoreCase = selector.ignoreCase) == true
}

/**
 * Walk up from [this] node looking for the nearest clickable ancestor and
 * try to click it. Useful when a "Skip Ad" label is itself non-clickable
 * but its parent FrameLayout is.
 *
 * Returns true if a click action was successfully dispatched.
 */
fun AccessibilityNodeInfo.clickSelfOrAncestor(): Boolean {
    var current: AccessibilityNodeInfo? = this
    var depth = 0
    while (current != null && depth < MAX_ANCESTOR_DEPTH) {
        if (current.isClickable && current.isEnabled) {
            return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        current = current.parent
        depth++
    }
    return false
}

/** Safety cap when climbing parents to avoid pathological deep trees. */
private const val MAX_ANCESTOR_DEPTH = 10
