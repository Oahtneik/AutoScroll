package com.autoscroll.app.data.model

/**
 * Snapshot of every user-controllable setting in the app.
 *
 * Held immutably and emitted as a Flow by [com.autoscroll.app.data.SettingsRepository].
 * UI observes and renders; mutations go through suspend setters on the repo,
 * never by mutating an instance of this class.
 *
 * @property isEnabled            Master switch. When false, the AccessibilityService
 *                                receives events but performs no swipe / no ad-skip.
 *                                Default OFF — required by Play Accessibility policy
 *                                (user must opt-in explicitly).
 *
 * @property scrollMode           See [ScrollMode] for the two strategies.
 *
 * @property timerSeconds         Used when [scrollMode] is [ScrollMode.BY_TIMER].
 *                                Coerced to [TIMER_RANGE] on read to defend against
 *                                bad persisted values (e.g. older app versions).
 *
 * @property skipAdsEnabled       If true, service tries to detect "Skip Ad" controls
 *                                and dismiss them. Pure convenience; can be off if
 *                                the user explicitly wants to see ads.
 *
 * @property enabledPlatforms     Subset of [Platform] on which auto-scroll is active.
 *                                Service still receives events from all 4 (defined
 *                                in accessibility_service_config.xml) but only acts
 *                                on platforms in this set.
 *
 * @property onboardingCompleted  Set true once the user has finished the first-run
 *                                onboarding (granted Accessibility + Overlay perms).
 *
 * @property showFloatingOverlay  Whether to show the floating quick-controls panel.
 *                                Some users may prefer to drive the app entirely from
 *                                the in-app screen and disable the overlay.
 */
data class AppSettings(
    val isEnabled: Boolean = false,
    val scrollMode: ScrollMode = ScrollMode.BY_TIMER,
    val timerSeconds: Int = DEFAULT_TIMER_SECONDS,
    val skipAdsEnabled: Boolean = true,
    val enabledPlatforms: Set<Platform> = Platform.entries.toSet(),
    val onboardingCompleted: Boolean = false,
    val showFloatingOverlay: Boolean = true,
) {
    companion object {
        const val DEFAULT_TIMER_SECONDS = 30

        /** Reasonable bounds for the timer slider in the UI and at-rest data. */
        val TIMER_RANGE: IntRange = 3..120
    }
}
