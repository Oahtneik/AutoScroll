package com.autoscroll.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Tiny helper: invoke [onResume] every time the host lifecycle hits ON_RESUME.
 *
 * Useful for permission checks — when the user returns from a system settings
 * page (Accessibility, Overlay) we need to refresh the "granted?" state to
 * reflect changes they made there.
 */
@Composable
fun OnResume(onResume: () -> Unit) {
    val cb = rememberUpdatedState(onResume)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cb.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
