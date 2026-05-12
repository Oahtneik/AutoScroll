package com.autoscroll.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autoscroll.app.data.model.AppSettings
import com.autoscroll.app.settingsRepository
import com.autoscroll.app.ui.screens.home.HomeScreen
import com.autoscroll.app.ui.screens.onboarding.OnboardingScreen
import com.autoscroll.app.ui.screens.settings.SettingsScreen

/**
 * Named routes for the NavHost. Sealed so adding a new screen forces every
 * `when` branch to be addressed at compile time.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home       : Screen("home")
    data object Settings   : Screen("settings")
}

/**
 * Top-level navigation. Start destination depends on whether the user has
 * already finished onboarding.
 *
 * Note we read `onboardingCompleted` ONCE at composition (not as a live
 * value) — once we navigate into Home we don't want a later flip of the
 * flag (e.g. via a "Reset onboarding" debug button) to teleport the user
 * back to onboarding.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val settings: AppSettings by context.settingsRepository.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    val navController = rememberNavController()
    val startDestination = if (settings.onboardingCompleted) Screen.Home.route
                           else Screen.Onboarding.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onCompleted = {
                    navController.navigate(Screen.Home.route) {
                        // Wipe onboarding off the back stack — back button on
                        // Home should exit the app, not re-enter onboarding.
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
