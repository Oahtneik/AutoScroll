package com.autoscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Owns the floating overlay window. One instance per foreground-service start.
 *
 * Responsibilities:
 *  1. Validate SYSTEM_ALERT_WINDOW permission (no crash if missing).
 *  2. Build a [ComposeView], wire up the three lifecycle owners required for
 *     Compose-in-Window via [OverlayLifecycleOwner].
 *  3. Add to [WindowManager] with the correct overlay window type/flags.
 *  4. Translate drag deltas into LayoutParams updates.
 *  5. Tear it all down on [dismiss].
 */
class OverlayController(
    private val context: Context,
    private val onTogglePause: () -> Unit,
    private val onCloseRequested: () -> Unit,
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleOwner = OverlayLifecycleOwner()
    private var composeView: ComposeView? = null
    private var isShown = false

    /** Layout params held as a field so we can mutate x/y on drag. */
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        // TYPE_APPLICATION_OVERLAY is the only legal type from API 26+ for
        // user-installed apps. TYPE_SYSTEM_ALERT and friends require system
        // signature.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        // FLAG_NOT_FOCUSABLE — keep keyboard / focus on the underlying app.
        // FLAG_LAYOUT_NO_LIMITS — let us position partially off-screen if user drags.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = INITIAL_X_PX
        y = INITIAL_Y_PX
    }

    /**
     * Adds the overlay to WindowManager. Safe to call multiple times — second
     * call is a no-op. Returns false if [Settings.canDrawOverlays] is false
     * (caller should prompt user to grant the permission).
     */
    fun show(): Boolean {
        if (isShown) return true
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted — overlay skipped")
            return false
        }

        lifecycleOwner.start()

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MaterialTheme {
                    CompositionLocalProvider {
                        FloatingPanel(
                            onDragDelta = ::onDragDelta,
                            onTogglePause = onTogglePause,
                            onClose = onCloseRequested,
                        )
                    }
                }
            }
        }
        composeView = view

        try {
            windowManager.addView(view, layoutParams)
            isShown = true
            Log.i(TAG, "Overlay added to WindowManager")
            return true
        } catch (e: WindowManager.BadTokenException) {
            // Should be rare since we checked canDrawOverlays — but defend anyway.
            Log.e(TAG, "Failed to add overlay (BadToken): ${e.message}")
            lifecycleOwner.stop()
            composeView = null
            return false
        }
    }

    /**
     * Removes the overlay if currently shown. Safe to call when not shown.
     */
    fun dismiss() {
        if (!isShown) return
        composeView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // View was already detached (e.g. orientation change race).
                Log.w(TAG, "removeView ignored: ${e.message}")
            }
        }
        composeView = null
        lifecycleOwner.stop()
        isShown = false
        Log.i(TAG, "Overlay dismissed")
    }

    private fun onDragDelta(dx: Int, dy: Int) {
        layoutParams.x += dx
        layoutParams.y += dy
        composeView?.let {
            try {
                windowManager.updateViewLayout(it, layoutParams)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "updateViewLayout ignored: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "OverlayController"

        // Initial position — top-right area, comfortable for right-handed thumbs.
        const val INITIAL_X_PX = 40
        const val INITIAL_Y_PX = 220
    }
}
