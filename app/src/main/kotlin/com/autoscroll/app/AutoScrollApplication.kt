package com.autoscroll.app

import android.app.Application
import android.content.Context
import com.autoscroll.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application entry point.
 *
 * Owns the singleton [SettingsRepository]. We do simple "manual DI" — any
 * component (Activity, AccessibilityService, ForegroundService) that needs
 * settings can grab the repo via the [Context.settingsRepository] extension
 * defined below. If the codebase grows past a handful of dependencies,
 * swap in Hilt with no changes to consumers.
 */
class AutoScrollApplication : Application() {

    /** Lazily initialised — first access happens after `onCreate` runs. */
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    /**
     * Process-scoped CoroutineScope. Use this for fire-and-forget I/O that
     * MUST complete even if the originating Service / Activity dies — most
     * notably the "set master switch off" write inside
     * AutoScrollForegroundService.stopSelfRequested(), where a lifecycleScope
     * would race the imminent stopSelf() and lose the write.
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Touch the repo so DataStore is warmed up early. Cheap, and means
        // first-screen reads don't pay the IO setup cost.
        settingsRepository
    }
}

/** Convenience accessor mirroring [settingsRepository]. */
val Context.applicationScope: CoroutineScope
    get() = (applicationContext as AutoScrollApplication).applicationScope

/**
 * Convenience accessor — works from any Context (Activity, Service, Application).
 *
 * Always use `applicationContext` chain inside, so callers can pass a short-lived
 * Context (e.g. an Activity) without leaking it into the singleton repo.
 */
val Context.settingsRepository: SettingsRepository
    get() = (applicationContext as AutoScrollApplication).settingsRepository
