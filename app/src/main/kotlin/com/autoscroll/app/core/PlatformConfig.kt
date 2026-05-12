package com.autoscroll.app.core

import com.autoscroll.app.data.model.Platform

/**
 * Per-platform UI selectors — data-driven so when YouTube/Instagram/Facebook/TikTok
 * change their UI (which they do, frequently), we only edit `PlatformConfigs.kt`,
 * not the engine.
 *
 * @property platform           Which platform this config applies to.
 *
 * @property adIndicators       Nodes whose presence on screen suggests the current
 *                              item is an ad/sponsored post. If found and there's
 *                              no [skipAdButton], we just trigger a swipe to move
 *                              past the ad.
 *
 * @property skipAdButton       Clickable nodes that, if tapped, dismiss the ad
 *                              (e.g. YouTube long-form "Skip Ad" button). Tried
 *                              FIRST — clicking is better than swiping if available.
 *
 * @property videoTimeIndicator Nodes that report the current video time (e.g.
 *                              "0:30 / 0:30"). Used by [VideoEndDetector] in
 *                              BY_VIDEO_END mode. May be empty for platforms
 *                              where this is too volatile to rely on.
 */
data class PlatformConfig(
    val platform: Platform,
    val adIndicators: List<NodeSelector>,
    val skipAdButton: List<NodeSelector>,
    val videoTimeIndicator: List<NodeSelector>,
)

/**
 * How to find a node in the AccessibilityNodeInfo tree.
 *
 * Sealed → exhaustive when()s, easy to add new selector types later
 * (e.g. ByClassName, ByCombination) without touching call sites.
 */
sealed class NodeSelector {
    /** Match by Android resource id, e.g. "com.google.android.youtube:id/skip_ad_button". */
    data class ByResourceId(val id: String) : NodeSelector()

    /** Exact text match. */
    data class ByText(val text: String, val ignoreCase: Boolean = true) : NodeSelector()

    /** Substring text match. */
    data class ByTextContains(val text: String, val ignoreCase: Boolean = true) : NodeSelector()

    /** Substring contentDescription match (useful for accessibility-labelled icons). */
    data class ByContentDescContains(val text: String, val ignoreCase: Boolean = true) : NodeSelector()
}
