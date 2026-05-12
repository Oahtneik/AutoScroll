package com.autoscroll.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.autoscroll.app.service.AutoScrollForegroundService
import com.autoscroll.app.ui.navigation.AppNavigation
import com.autoscroll.app.ui.theme.AutoScrollTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Single Activity, single content. Everything else lives inside
 * [AppNavigation] (NavHost) and the engine/service layer.
 *
 * Side responsibility: observe the master switch and start/stop the
 * foreground service accordingly. Putting this here (rather than inside
 * a screen composable) means the observer survives navigation between
 * Home and Settings — there's no risk of "screen change → service flip".
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() MUST be called before super.onCreate so the
        // splash window has time to attach before any content is drawn.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoScrollTheme {
                ForegroundServiceObserver()
                AppNavigation()
            }
        }
    }
}

/**
 * Mirrors the user's master switch to the foreground-service lifecycle.
 *
 * `distinctUntilChanged` so we don't churn on unrelated settings updates
 * (timer slider drag, platform toggles, …) that don't actually flip
 * `isEnabled`.
 */
@Composable
private fun ForegroundServiceObserver() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        context.settingsRepository.settingsFlow
            .map { it.isEnabled }
            .distinctUntilChanged()
            .collect { enabled ->
                if (enabled) {
                    AutoScrollForegroundService.Controller.start(context)
                } else {
                    AutoScrollForegroundService.Controller.stop(context)
                }
            }
    }
}
