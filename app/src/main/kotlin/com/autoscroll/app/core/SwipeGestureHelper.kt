package com.autoscroll.app.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

/**
 * Dispatches a vertical swipe via [AccessibilityService.dispatchGesture].
 *
 * Why a class (not just a free function): we wrap the service so call sites
 * don't need to remember to pass it every time, and so we can add screen-size
 * caching later if we measure it as a hot path.
 *
 * Geometry: we swipe from 75% screen height to 25% — long enough that every
 * tested platform reliably advances to the next item. Going edge-to-edge
 * (90% → 10%) sometimes triggers system gestures (back, recent apps).
 *
 * Duration: 250ms — short enough to feel snappy, long enough that the host
 * app's gesture detector classifies it as a fling rather than a tap-and-hold.
 */
class SwipeGestureHelper(private val service: AccessibilityService) {

    /**
     * Swipe up — moves to the NEXT video on every supported platform.
     *
     * @param onResult invoked on the main thread once the gesture completes
     *                 or is cancelled. `true` = completed, `false` = cancelled.
     *                 Pass an empty lambda if you don't care.
     */
    fun swipeUp(onResult: (Boolean) -> Unit = {}) {
        val metrics = service.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()

        val path = Path().apply {
            moveTo(w / 2f, h * SWIPE_FROM_RATIO)
            lineTo(w / 2f, h * SWIPE_TO_RATIO)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) { onResult(true) }
            override fun onCancelled(g: GestureDescription) {
                Log.w(TAG, "swipeUp cancelled by system")
                onResult(false)
            }
        }

        val dispatched = service.dispatchGesture(gesture, callback, /* handler = */ null)
        if (!dispatched) {
            // Service may be temporarily disconnected (e.g. user is in a
            // secure window — Settings > Lock screen — that blocks gestures).
            Log.w(TAG, "dispatchGesture returned false (service not ready?)")
            onResult(false)
        }
    }

    private companion object {
        const val TAG = "SwipeGestureHelper"
        const val SWIPE_FROM_RATIO = 0.75f
        const val SWIPE_TO_RATIO = 0.25f
        const val SWIPE_DURATION_MS = 250L
    }
}
