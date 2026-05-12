package com.autoscroll.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autoscroll.app.data.model.AppSettings
import com.autoscroll.app.data.model.Platform
import com.autoscroll.app.data.model.ScrollMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The DataStore file name. Backed up via the rules in
 * `res/xml/data_extraction_rules.xml`, so user settings survive a phone
 * migration.
 */
private const val DATA_STORE_NAME = "autoscroll_settings"

/**
 * Top-level extension property — DataStore Preferences are required to be
 * a singleton per file name. The delegate handles that automatically.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME,
)

/**
 * Single source of truth for [AppSettings].
 *
 * Constructed once in [com.autoscroll.app.AutoScrollApplication] and reused
 * by every consumer (UI ViewModels, AccessibilityService, ForegroundService).
 *
 * Pass `applicationContext` — never an Activity context — to avoid leaks.
 */
class SettingsRepository(private val context: Context) {

    // ---- Preference keys ------------------------------------------------
    // Kept private so the rest of the codebase can only go through the
    // typed Flow + setters below.
    private object Keys {
        val IS_ENABLED         = booleanPreferencesKey("is_enabled")
        val SCROLL_MODE        = stringPreferencesKey("scroll_mode")
        val TIMER_SECONDS      = intPreferencesKey("timer_seconds")
        val SKIP_ADS           = booleanPreferencesKey("skip_ads")
        val ENABLED_PLATFORMS  = stringSetPreferencesKey("enabled_platforms")
        val ONBOARDING_DONE    = booleanPreferencesKey("onboarding_completed")
        val SHOW_OVERLAY       = booleanPreferencesKey("show_overlay")
    }

    // ---- Read ------------------------------------------------------------

    /**
     * Hot stream of the current settings. Emits a fresh value every time
     * any setter below mutates the underlying file.
     *
     * `catch` swallows DataStore I/O errors and emits empty preferences,
     * which then map to [AppSettings] defaults — i.e. the app keeps working
     * even if the file is briefly unreadable (e.g. during backup/restore).
     */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs.toAppSettings() }

    // ---- Write -----------------------------------------------------------
    // Each setter is suspend + edits a single key for clarity. DataStore
    // serialises writes internally, so concurrent calls are safe.

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    suspend fun setScrollMode(mode: ScrollMode) {
        context.dataStore.edit { it[Keys.SCROLL_MODE] = mode.name }
    }

    suspend fun setTimerSeconds(seconds: Int) {
        // Defensive coerce — UI should already clamp, but never trust input.
        val clamped = seconds.coerceIn(AppSettings.TIMER_RANGE)
        context.dataStore.edit { it[Keys.TIMER_SECONDS] = clamped }
    }

    suspend fun setSkipAdsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SKIP_ADS] = enabled }
    }

    suspend fun setEnabledPlatforms(platforms: Set<Platform>) {
        context.dataStore.edit {
            it[Keys.ENABLED_PLATFORMS] = platforms.map { p -> p.name }.toSet()
        }
    }

    suspend fun togglePlatform(platform: Platform, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_PLATFORMS]
                ?: Platform.entries.map { it.name }.toSet()
            prefs[Keys.ENABLED_PLATFORMS] = if (enabled) {
                current + platform.name
            } else {
                current - platform.name
            }
        }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setShowFloatingOverlay(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_OVERLAY] = show }
    }

    // ---- Mapping ---------------------------------------------------------

    /**
     * Map raw [Preferences] → typed [AppSettings].
     *
     * - Missing keys fall back to [AppSettings] defaults.
     * - Bad enum names (e.g. removed in a future version) fall back to the
     *   default rather than throwing — this keeps old clients running after
     *   schema-shrinking migrations.
     */
    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()

        val mode = this[Keys.SCROLL_MODE]
            ?.let { runCatching { ScrollMode.valueOf(it) }.getOrNull() }
            ?: defaults.scrollMode

        val platforms = this[Keys.ENABLED_PLATFORMS]
            ?.mapNotNull { runCatching { Platform.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: defaults.enabledPlatforms

        return AppSettings(
            isEnabled            = this[Keys.IS_ENABLED]      ?: defaults.isEnabled,
            scrollMode           = mode,
            timerSeconds         = (this[Keys.TIMER_SECONDS]  ?: defaults.timerSeconds)
                                   .coerceIn(AppSettings.TIMER_RANGE),
            skipAdsEnabled       = this[Keys.SKIP_ADS]        ?: defaults.skipAdsEnabled,
            enabledPlatforms     = platforms,
            onboardingCompleted  = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingCompleted,
            showFloatingOverlay  = this[Keys.SHOW_OVERLAY]    ?: defaults.showFloatingOverlay,
        )
    }
}
