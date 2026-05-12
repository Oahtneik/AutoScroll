package com.autoscroll.app.core

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Best-effort detector for "video has finished playing".
 *
 * **Honest caveat:** short-video platforms (YouTube Shorts, Reels, TikTok)
 * AUTO-LOOP each video, so there is rarely a clean "video ended" UI signal.
 * The most reliable cross-platform signal we have today is to watch the
 * video time text and detect when the displayed time decreases (= a loop
 * just happened, i.e. the video had reached its end).
 *
 * For platforms whose [PlatformConfig.videoTimeIndicator] is empty, we have
 * no signal at all and the engine falls back to a max-duration timer.
 *
 * TODO (post-MVP): per-platform exploration via uiautomator dump to find more
 *      reliable signals (e.g. hidden "Replay" affordances, progress-bar
 *      SeekBar nodes with progress == max).
 */
class VideoEndDetector {

    private var lastSeenSeconds: Int? = null

    /**
     * Reset state — call when the user switches platform / window so we don't
     * carry state across unrelated videos.
     */
    fun reset() {
        lastSeenSeconds = null
    }

    /**
     * Inspect the current root node for a video-time text. If the time has
     * decreased since the last call, that strongly suggests the previous
     * video looped (i.e. ended).
     *
     * @return true exactly once per detected end. Subsequent calls return
     *         false until the time progresses past the loop point again.
     */
    fun didVideoEnd(rootNode: AccessibilityNodeInfo?, config: PlatformConfig): Boolean {
        if (rootNode == null) return false
        if (config.videoTimeIndicator.isEmpty()) return false

        val timeText = config.videoTimeIndicator
            .firstNotNullOfOrNull { rootNode.findFirst(it)?.text?.toString() }
            ?: return false

        val seconds = parseSecondsLoose(timeText) ?: return false

        val previous = lastSeenSeconds
        lastSeenSeconds = seconds

        // Detect a strict decrease — i.e. time wrapped from end back to start.
        // We require previous > seconds + 2 to avoid noise from time-text
        // round-down jitter ("0:09" → "0:08" shouldn't fire).
        return previous != null && previous > seconds + LOOP_HYSTERESIS_SECONDS
    }

    /**
     * Parse a time string like "0:42", "1:23", "0:42 / 0:45" into seconds.
     * Returns null if not parseable.
     */
    private fun parseSecondsLoose(raw: String): Int? {
        // Take the FIRST m:ss token in the string (handles "0:42 / 0:45").
        val match = TIME_REGEX.find(raw) ?: return null
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val seconds = match.groupValues[2].toIntOrNull() ?: return null
        return minutes * 60 + seconds
    }

    private companion object {
        val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})""")
        const val LOOP_HYSTERESIS_SECONDS = 2
    }
}
