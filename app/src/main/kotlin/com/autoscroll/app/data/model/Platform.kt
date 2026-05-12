package com.autoscroll.app.data.model

/**
 * The four short-video platforms AutoScroll supports.
 *
 * `packageNames` lists every Android package name a platform can ship under.
 * TikTok in particular has multiple package IDs depending on region (the
 * global build vs the older "trill" build). We match on the full set so
 * the service works regardless of which one the user installed.
 *
 * If/when a platform changes its package name (rare), only this enum needs
 * to be touched — and the corresponding entry must also be added to
 * `accessibility_service_config.xml` (`android:packageNames` attribute).
 */
enum class Platform(
    val displayName: String,
    val packageNames: List<String>,
) {
    YOUTUBE(
        displayName = "YouTube Shorts",
        packageNames = listOf("com.google.android.youtube"),
    ),
    INSTAGRAM(
        displayName = "Instagram Reels",
        packageNames = listOf("com.instagram.android"),
    ),
    FACEBOOK(
        displayName = "Facebook Reels",
        packageNames = listOf("com.facebook.katana"),
    ),
    TIKTOK(
        displayName = "TikTok",
        packageNames = listOf(
            "com.zhiliaoapp.musically",  // global / most regions
            "com.ss.android.ugc.trill",  // some Asian regions / older builds
        ),
    ),
    ;

    companion object {
        /**
         * Reverse lookup: given a foreground app's package name, return the
         * matching [Platform] or null if it is not a supported app.
         */
        fun fromPackageName(packageName: String?): Platform? {
            if (packageName.isNullOrBlank()) return null
            return entries.firstOrNull { packageName in it.packageNames }
        }
    }
}
