package com.autoscroll.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = BrandSeed,
    onPrimary = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
)

private val DarkScheme = darkColorScheme(
    primary = BrandSeedDark,
    onPrimary = SurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
)

/**
 * App-wide theme. Follow the system Light/Dark setting unless overridden.
 *
 * We deliberately don't enable Material You dynamic colours: the visual
 * identity should feel like "AutoScroll" everywhere, not tinted by whatever
 * wallpaper the user has set this week.
 */
@Composable
fun AutoScrollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}
