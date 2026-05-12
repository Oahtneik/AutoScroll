package com.autoscroll.app.ui.util

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.autoscroll.app.service.AutoScrollAccessibilityService

/**
 * Returns true iff the user has enabled OUR specific accessibility service
 * in system Settings.
 *
 * Implementation note — we compare on `packageName` + canonical class name
 * rather than substring matching on the service id, because:
 *   - Other apps could share part of our package name (unlikely but possible).
 *   - Debug builds carry the applicationId `com.autoscroll.app.debug` while
 *     the service class still lives in `com.autoscroll.app`, so a naive
 *     packageName-only check would fail on debug.
 *
 * The feedbackType `-1` means "all feedback types"; our service reports
 * GENERIC but we don't care about the type here, only the enabled-ness.
 */
fun isAutoScrollAccessibilityServiceEnabled(context: Context): Boolean {
    val am = ContextCompat.getSystemService(context, AccessibilityManager::class.java)
        ?: return false
    val ourClassName = AutoScrollAccessibilityService::class.java.name
    val ourPackageName = context.packageName

    return am.getEnabledAccessibilityServiceList(/* feedbackTypeFlags = */ -1)
        .orEmpty()
        .any { info ->
            val svc = info.resolveInfo?.serviceInfo ?: return@any false
            svc.packageName == ourPackageName && svc.name == ourClassName
        }
}
