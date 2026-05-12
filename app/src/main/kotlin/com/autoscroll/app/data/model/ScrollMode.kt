package com.autoscroll.app.data.model

/**
 * Two strategies for deciding when to scroll to the next video.
 *
 * - [BY_TIMER]      : swipe after a fixed number of seconds. Simplest,
 *                     works on every platform, but may cut a video short
 *                     or wait too long after a short one finishes.
 *
 * - [BY_VIDEO_END]  : swipe when the AccessibilityService detects the
 *                     current video has finished playing (via UI signals
 *                     such as the replay button appearing, progress bar
 *                     reset, or duration node reaching its max). More
 *                     natural feel, but each platform needs its own
 *                     detection logic and may break when their UI updates.
 *                     We fall back to TIMER if detection fails for too long.
 */
enum class ScrollMode {
    BY_TIMER,
    BY_VIDEO_END,
}
