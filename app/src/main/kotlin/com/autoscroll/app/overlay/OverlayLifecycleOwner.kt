package com.autoscroll.app.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal owner triple required by Jetpack Compose when a [androidx.compose.ui.platform.ComposeView]
 * is attached outside an Activity / Fragment hierarchy — i.e. inside a
 * [android.view.WindowManager] overlay window.
 *
 * Without these owners, ComposeView crashes immediately with:
 *   "ViewTreeLifecycleOwner not found from DecorView"
 *
 * We don't need real lifecycle/state restoration for an overlay — there is
 * nothing to save — so the implementation is intentionally tiny: jump straight
 * to RESUMED in [start], to DESTROYED in [stop].
 *
 * Usage from [OverlayController]:
 * ```
 * val owner = OverlayLifecycleOwner().also { it.start() }
 * composeView.setViewTreeLifecycleOwner(owner)
 * composeView.setViewTreeViewModelStoreOwner(owner)
 * composeView.setViewTreeSavedStateRegistryOwner(owner)
 * // ...
 * owner.stop()
 * ```
 */
internal class OverlayLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun start() {
        // SavedStateRegistry needs attach + restore even if we won't read state.
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
