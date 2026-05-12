package com.autoscroll.app.core

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detect ads / sponsored content and dispose of them.
 *
 * Strategy (per platform):
 *  1. Try to click a Skip-Ad button if the platform has one (YouTube long-form).
 *  2. Otherwise, if any "Sponsored / Ad" indicator is on screen, request a swipe
 *     so we move past the ad rather than waiting through it.
 *  3. If neither, no-op.
 *
 * The swipe itself is NOT performed here — we ask the engine to swipe via
 * [onSwipeRequested]. This keeps gesture dispatch in one place
 * ([SwipeGestureHelper]) and avoids tight coupling to a service instance.
 */
object AdSkipper {

    /**
     * @param rootNode         current window's root, may be null if the window
     *                         is mid-transition.
     * @param config           selectors for the active platform.
     * @param onSwipeRequested called on main thread if we want the engine to
     *                         dispatch a swipe-up (ad indicator found, no
     *                         skip button available).
     *
     * @return one of [Result] — purely informational, used for logging /
     *         overlay status updates.
     */
    fun handle(
        rootNode: AccessibilityNodeInfo?,
        config: PlatformConfig,
        onSwipeRequested: () -> Unit,
    ): Result {
        if (rootNode == null) return Result.NoAd

        // 1) Try to click a skip-ad button.
        for (selector in config.skipAdButton) {
            val node = rootNode.findFirst(selector) ?: continue
            val clicked = node.clickSelfOrAncestor()
            if (clicked) {
                Log.d(TAG, "Skip-ad button clicked via $selector on ${config.platform}")
                return Result.SkippedViaButton
            }
            // Found but not clickable — keep looking.
        }

        // 2) Indicator present → swipe past.
        for (selector in config.adIndicators) {
            if (rootNode.containsMatching(selector)) {
                Log.d(TAG, "Ad indicator $selector matched on ${config.platform}; requesting swipe")
                onSwipeRequested()
                return Result.SkippedViaSwipe
            }
        }

        return Result.NoAd
    }

    enum class Result { NoAd, SkippedViaButton, SkippedViaSwipe }

    private const val TAG = "AdSkipper"
}
