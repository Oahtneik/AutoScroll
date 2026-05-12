package com.autoscroll.app.core

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.autoscroll.app.data.SettingsRepository
import com.autoscroll.app.data.model.AppSettings
import com.autoscroll.app.data.model.Platform
import com.autoscroll.app.data.model.ScrollMode
import com.autoscroll.app.service.AutoScrollAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Brain of the app.
 *
 * Singleton (Kotlin `object`) so its state survives short-lived recreations
 * of [AutoScrollAccessibilityService] and so the (Phase-5) floating overlay
 * can read state without binding to a service.
 *
 * Threading: every public entry point is called from the main thread (the
 * AccessibilityService callbacks run there). Internal coroutines also run
 * on Dispatchers.Main. No locks are needed.
 */
object AutoScrollEngine {

    // ---- Public state ---------------------------------------------------

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // ---- Internal --------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var settingsCollector: Job? = null
    private var timerJob: Job? = null
    private var watchdogJob: Job? = null
    private var demotionJob: Job? = null

    private var service: AutoScrollAccessibilityService? = null
    private var swipeHelper: SwipeGestureHelper? = null
    private val videoEndDetector = VideoEndDetector()

    /** Latest settings — kept in a field for synchronous access in event handlers. */
    private var currentSettings: AppSettings = AppSettings()

    /** Throttle + cooldown timestamps (uptime millis, not wallclock). */
    private var lastAdCheckAt: Long = 0L
    private var lastSwipeAt: Long = 0L

    // ---- Lifecycle (called by the AccessibilityService) ------------------

    /**
     * Hand the engine a freshly-connected service. Begin observing settings
     * and prepare to react to events.
     */
    fun attach(
        service: AutoScrollAccessibilityService,
        settingsRepository: SettingsRepository,
    ) {
        this.service = service
        this.swipeHelper = SwipeGestureHelper(service)

        settingsCollector?.cancel()
        settingsCollector = scope.launch {
            settingsRepository.settingsFlow.collect { newSettings ->
                currentSettings = newSettings
                reconcile()
            }
        }

        // Bootstrap: we just connected, no WINDOW_STATE_CHANGED has fired
        // yet for whatever app was already in the foreground (e.g. user had
        // YouTube open before we connected). Pull it from the service directly.
        refreshFromService()

        // Watchdog: re-query foreground every WATCHDOG_INTERVAL_MS. On
        // HyperOS / aggressive-OEM ROMs we sometimes lose WINDOW_STATE_CHANGED
        // events; this poll guarantees we eventually catch up.
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                refreshFromService()
            }
        }

        Log.i(TAG, "Engine attached to AccessibilityService")
    }

    /**
     * Re-read the foreground packageName directly from the AccessibilityService.
     * Called on attach, on FGS start, and periodically by the watchdog.
     *
     * IMPORTANT — this is "promote-only":
     *   - If we find a real foreground packageName, forward it.
     *   - If we get null, do NOTHING (don't reset state).
     *
     * Why: `rootInActiveWindow` is designed to be called from inside an
     * AccessibilityEvent callback. From a background coroutine — including
     * the 2-second watchdog — it frequently returns null even when the user
     * is plainly on YouTube. If we trusted that null we'd flicker the engine
     * state Platform → null → Platform → null roughly every 2 seconds.
     *
     * The demotion case ("user really left YouTube") is handled exclusively
     * by real AccessibilityEvents whose `event.packageName` is authoritative.
     */
    fun refreshFromService() {
        val s = service ?: return
        val pkg = try {
            s.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            // Some OEM ROMs throw SecurityException on this getter when the
            // active window belongs to a system surface (lock screen, etc.).
            Log.w(TAG, "rootInActiveWindow threw: ${e.message}")
            null
        }
        // Promote-only: never reset to null from a watchdog poll.
        if (pkg != null) {
            onWindowChanged(pkg)
        }
    }

    /**
     * Service is being destroyed. Stop everything and release the reference
     * so we don't leak the service Context.
     */
    fun detach() {
        timerJob?.cancel()
        settingsCollector?.cancel()
        watchdogJob?.cancel()
        demotionJob?.cancel()
        timerJob = null
        settingsCollector = null
        watchdogJob = null
        demotionJob = null
        service = null
        swipeHelper = null
        videoEndDetector.reset()
        _state.value = EngineState()
        Log.i(TAG, "Engine detached")
    }

    // ---- AccessibilityService event hooks --------------------------------

    /**
     * The foreground app may have changed.
     *
     * Promotion (non-target → target, or target → different target) is applied
     * IMMEDIATELY: this matches user intent of "I just opened YouTube".
     *
     * Demotion (target → non-target) is DEBOUNCED for [DEMOTION_DEBOUNCE_MS]
     * because YouTube playback frequently produces interleaved accessibility
     * events from system surfaces (status bar refresh, IME warm-up, MIUI
     * overlays). Without debouncing we'd flicker between "YouTube" and
     * "Standby" several times a second. Any target event arriving within the
     * debounce window cancels the pending demotion.
     */
    fun onWindowChanged(packageName: String?) {
        val newPlatform = Platform.fromPackageName(packageName)
        val currentPlatform = _state.value.currentPlatform

        if (newPlatform != null) {
            // ---- Promotion path ----
            // A target app is visibly the foreground. Cancel any pending
            // demotion regardless of whether this is a state change.
            demotionJob?.cancel()
            demotionJob = null

            if (newPlatform != currentPlatform) {
                videoEndDetector.reset()
                _state.update { it.copy(currentPlatform = newPlatform) }
                reconcile()
            }
            return
        }

        // ---- Demotion path ----
        // Event is from a non-target package. Schedule a debounced demotion
        // only if we are currently ON a target and not already scheduled.
        if (currentPlatform != null && demotionJob == null) {
            demotionJob = scope.launch {
                delay(DEMOTION_DEBOUNCE_MS)
                _state.update { it.copy(currentPlatform = null) }
                videoEndDetector.reset()
                reconcile()
                demotionJob = null
            }
        }
        // Else: already standby OR demotion already pending → no-op.
    }

    /**
     * The user (or a third party) scrolled inside a host app. Reset the
     * countdown timer so they don't get auto-swiped a second after their
     * own swipe lands them on a fresh video.
     *
     * Cooldown check: we ignore scrolls that come within
     * [POST_SWIPE_COOLDOWN_MS] of our own swipe, because the scroll the
     * host produced in response to our gesture would otherwise reset the
     * timer twice in quick succession.
     */
    fun onUserScroll() {
        val now = SystemClock.uptimeMillis()
        if (now - lastSwipeAt < POST_SWIPE_COOLDOWN_MS) return
        if (!isCurrentlyActive()) return
        Log.d(TAG, "User scroll detected — resetting countdown")
        restartTimerIfNeeded()
    }

    /**
     * Window content changed (UI updated within current screen). Used for:
     *  - ad detection (throttled; see [AD_CHECK_THROTTLE_MS])
     *  - BY_VIDEO_END detection
     *
     * Cooldown: we ignore content events for [POST_SWIPE_COOLDOWN_MS] after
     * we just swiped, because the swipe itself causes a flurry of content
     * events that would otherwise trigger spurious re-detection.
     */
    fun onContentChanged(rootNode: AccessibilityNodeInfo?) {
        if (!isCurrentlyActive()) return

        val now = SystemClock.uptimeMillis()
        if (now - lastSwipeAt < POST_SWIPE_COOLDOWN_MS) return
        if (now - lastAdCheckAt < AD_CHECK_THROTTLE_MS) return
        lastAdCheckAt = now

        val platform = _state.value.currentPlatform ?: return
        val config = PLATFORM_CONFIGS[platform] ?: return

        // ---- Ad skipping ----
        if (currentSettings.skipAdsEnabled) {
            val result = AdSkipper.handle(
                rootNode = rootNode,
                config = config,
                onSwipeRequested = { performSwipe(reason = "ad") },
            )
            if (result == AdSkipper.Result.SkippedViaButton) {
                // Reset countdown so user doesn't get a double-swipe right after.
                restartTimerIfNeeded()
            }
        }

        // ---- BY_VIDEO_END detection ----
        if (currentSettings.scrollMode == ScrollMode.BY_VIDEO_END) {
            if (videoEndDetector.didVideoEnd(rootNode, config)) {
                Log.d(TAG, "Video end detected on ${platform.displayName}")
                performSwipe(reason = "video-end")
                restartTimerIfNeeded()
            }
        }
    }

    // ---- Reconcile -------------------------------------------------------

    /**
     * Bring engine state into agreement with [currentSettings] +
     * [_state.value.currentPlatform]. Called whenever any of those change.
     */
    private fun reconcile() {
        val active = isCurrentlyActive()
        _state.update { it.copy(isActive = active) }

        if (active) {
            restartTimerIfNeeded()
        } else {
            stopTimer()
        }
    }

    private fun isCurrentlyActive(): Boolean {
        if (!currentSettings.isEnabled) return false
        val platform = _state.value.currentPlatform ?: return false
        return platform in currentSettings.enabledPlatforms
    }

    // ---- Timer (BY_TIMER + BY_VIDEO_END fallback) ------------------------

    /**
     * (Re)start the countdown timer that drives BY_TIMER mode.
     *
     * For BY_VIDEO_END mode we still run a timer, but with a longer ceiling
     * — it acts as a fallback in case detection misses the loop point.
     */
    private fun restartTimerIfNeeded() {
        if (!isCurrentlyActive()) return

        timerJob?.cancel()
        timerJob = scope.launch {
            val ceiling = ceilingForMode(currentSettings)
            while (isActive && isCurrentlyActive()) {
                for (remaining in ceiling downTo 1) {
                    if (!isActive || !isCurrentlyActive()) return@launch
                    _state.update { it.copy(secondsRemaining = remaining) }
                    delay(1_000)
                }
                performSwipe(reason = "timer")
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(secondsRemaining = 0) }
    }

    private fun ceilingForMode(s: AppSettings): Int = when (s.scrollMode) {
        ScrollMode.BY_TIMER -> s.timerSeconds
        // 1.5x the timer as fallback ceiling — see VideoEndDetector class doc
        // for why BY_VIDEO_END is best-effort and needs a fallback.
        ScrollMode.BY_VIDEO_END -> (s.timerSeconds * 3 / 2).coerceAtLeast(s.timerSeconds + 5)
    }

    // ---- Swipe -----------------------------------------------------------

    private fun performSwipe(reason: String) {
        val helper = swipeHelper ?: return
        lastSwipeAt = SystemClock.uptimeMillis()
        Log.d(TAG, "Performing swipe ($reason)")
        helper.swipeUp()
    }

    // ---- Constants -------------------------------------------------------

    private const val TAG = "AutoScrollEngine"

    /** Don't traverse the tree more than ~twice a second. */
    private const val AD_CHECK_THROTTLE_MS = 500L

    /** Ignore content events for this long after a swipe (host app is settling). */
    private const val POST_SWIPE_COOLDOWN_MS = 1_500L

    /**
     * How often the watchdog re-queries the foreground app. Cheap call
     * ([android.view.accessibility.AccessibilityNodeInfo.getPackageName] on
     * the root) — picking 2s as a balance between responsiveness after
     * missed events and not waking the CPU too often.
     */
    private const val WATCHDOG_INTERVAL_MS = 2_000L

    /**
     * Grace period before we treat "target → non-target" as a real app
     * switch. Tuned to absorb transient system-surface events while still
     * feeling responsive when the user genuinely leaves the host app.
     */
    private const val DEMOTION_DEBOUNCE_MS = 1_000L
}

/**
 * Read-only snapshot of engine state. Exposed so the (Phase 5) overlay can
 * render countdown / "scrolling on YouTube" text without coupling to internals.
 */
data class EngineState(
    val currentPlatform: Platform? = null,
    val isActive: Boolean = false,
    val secondsRemaining: Int = 0,
)
