package com.autoscroll.app.core

import com.autoscroll.app.data.model.Platform

/**
 * The actual rule book.
 *
 * IMPORTANT — these selectors are BEST-EFFORT and will go stale as the host
 * apps update their UI. When auto-scroll/skip-ad stops working on a given
 * platform, this is the FIRST place to check. To audit a real device:
 *
 *   1. Enable Developer options > "Show layout bounds" or use
 *      `adb shell uiautomator dump` and pull window_dump.xml.
 *   2. Find the resource-id / text of the new node and update the entry below.
 *
 * Don't be surprised that ad/sponsored selectors include English-only strings;
 * these short-video apps localise content but the "Sponsored" / "Ad" tags are
 * usually rendered as English in code-paths even on localised builds. If we
 * see misses in non-EN locales, expand this list.
 */
val PLATFORM_CONFIGS: Map<Platform, PlatformConfig> = mapOf(

    // -------------------------------------------------------------------
    // YouTube (Shorts + long-form pre-roll ads)
    // -------------------------------------------------------------------
    Platform.YOUTUBE to PlatformConfig(
        platform = Platform.YOUTUBE,
        adIndicators = listOf(
            NodeSelector.ByText("Sponsored"),
            NodeSelector.ByContentDescContains("Sponsored"),
            // Reels-style "Ad" badge — YouTube Shorts shows this on promoted
            // content from time to time.
            NodeSelector.ByText("Ad"),
        ),
        skipAdButton = listOf(
            NodeSelector.ByResourceId("com.google.android.youtube:id/skip_ad_button"),
            NodeSelector.ByResourceId("com.google.android.youtube:id/skip_ad_button_text"),
            NodeSelector.ByContentDescContains("Skip ad"),
            NodeSelector.ByTextContains("Skip ad"),
        ),
        videoTimeIndicator = listOf(
            // Shorts overlay rarely exposes a time text node; long-form does:
            NodeSelector.ByResourceId("com.google.android.youtube:id/time_bar_current_time"),
        ),
    ),

    // -------------------------------------------------------------------
    // Instagram (Reels)
    // -------------------------------------------------------------------
    Platform.INSTAGRAM to PlatformConfig(
        platform = Platform.INSTAGRAM,
        adIndicators = listOf(
            NodeSelector.ByText("Sponsored"),
            NodeSelector.ByContentDescContains("Sponsored"),
            NodeSelector.ByTextContains("Paid partnership"),
        ),
        // Instagram has no "Skip" button on Reels ads — user just swipes past.
        // We deliberately leave this empty so AdSkipper falls through to swipe.
        skipAdButton = emptyList(),
        videoTimeIndicator = emptyList(),
    ),

    // -------------------------------------------------------------------
    // Facebook (Reels)
    // -------------------------------------------------------------------
    Platform.FACEBOOK to PlatformConfig(
        platform = Platform.FACEBOOK,
        adIndicators = listOf(
            NodeSelector.ByText("Sponsored"),
            NodeSelector.ByContentDescContains("Sponsored"),
            // Facebook localises "Sponsored" to "Được tài trợ" in Vietnamese:
            NodeSelector.ByTextContains("Được tài trợ"),
        ),
        skipAdButton = emptyList(),
        videoTimeIndicator = emptyList(),
    ),

    // -------------------------------------------------------------------
    // TikTok (For You feed)
    // -------------------------------------------------------------------
    Platform.TIKTOK to PlatformConfig(
        platform = Platform.TIKTOK,
        adIndicators = listOf(
            NodeSelector.ByText("Sponsored"),
            NodeSelector.ByText("Ad"),
            NodeSelector.ByContentDescContains("Sponsored"),
            NodeSelector.ByTextContains("LearnMore"),  // "Learn more" CTA on ads
        ),
        skipAdButton = emptyList(),
        videoTimeIndicator = emptyList(),
    ),
)
