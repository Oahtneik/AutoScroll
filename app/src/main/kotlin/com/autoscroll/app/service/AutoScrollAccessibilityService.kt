package com.autoscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.autoscroll.app.core.AutoScrollEngine
import com.autoscroll.app.data.model.Platform
import com.autoscroll.app.settingsRepository

/**
 * Thin Android facade. ALL real logic lives in [AutoScrollEngine].
 *
 * This class only:
 *  - tells the engine when we're connected/disconnected,
 *  - forwards relevant accessibility events.
 *
 * Keeping it thin means we don't lose state if Android recreates the service
 * (e.g. when the user toggles it off/on), and we can unit-test the engine
 * without mocking the Android service framework.
 */
class AutoScrollAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected — registering with engine")
        AutoScrollEngine.attach(
            service = this,
            settingsRepository = applicationContext.settingsRepository,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // System surfaces (status bar, launcher, IME) fire transient
                // STATE_CHANGED events all the time while a video is playing.
                // We never want to interpret those as "user left YouTube",
                // so blacklist them and let the engine continue trusting its
                // current state.
                if (packageName != null && packageName in NON_APP_PACKAGES) return
                AutoScrollEngine.onWindowChanged(packageName)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // ONLY use CONTENT_CHANGED if it comes from a target app.
                // System overlays produce a flurry of these with their own
                // packageName during playback; counting those as "user
                // switched apps" was the root cause of the YouTube flicker.
                if (Platform.fromPackageName(packageName) != null) {
                    AutoScrollEngine.onWindowChanged(packageName)  // heartbeat
                    AutoScrollEngine.onContentChanged(rootInActiveWindow)
                }
                // else: silently drop. Engine state stays as-is.
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // User (or our own gesture) scrolled inside a host app —
                // signal the engine so it can reset the countdown. The
                // engine itself filters out scrolls that immediately follow
                // an app-issued swipe (cooldown).
                if (Platform.fromPackageName(packageName) != null) {
                    AutoScrollEngine.onUserScroll()
                }
            }
        }
    }

    private companion object {
        /**
         * Packages whose STATE_CHANGED events we ignore. These belong to
         * system surfaces that briefly take focus but do NOT represent the
         * user leaving the underlying app. Add new entries here when you
         * find a ROM that flickers AutoScroll for an unlisted package.
         */
        val NON_APP_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.miui.home",
            "com.mi.android.globallauncher",   // POCO/HyperOS launcher
            "com.miui.systemAdSolution",
            "com.google.android.inputmethod.latin",
            "com.miui.securitycenter",
        )

        const val TAG = "AutoScrollA11yService"
    }
}

    /**
     * Called when the system asks us to stop processing (e.g. another a11y
     * service is taking exclusive control). We just log; the engine keeps
     * its current settings observation and will resume when events flow again.
     */
    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt() called by system")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — detaching engine")
        AutoScrollEngine.detach()
        super.onDestroy()
    }
}
