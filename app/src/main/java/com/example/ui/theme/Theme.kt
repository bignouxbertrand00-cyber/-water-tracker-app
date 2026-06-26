package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DensityPrimary,
    secondary = DensitySecondary,
    tertiary = DensityTertiary,
    background = DensityBackgroundDark,
    surface = DensitySurfaceDark,
    onPrimary = DensityOnPrimaryDark,
    onBackground = DensityOnSurfaceDark,
    onSurface = DensityOnSurfaceDark,
    surfaceVariant = Color(0xFF323B45)
)

private val LightColorScheme = lightColorScheme(
    primary = DensityPrimary,
    secondary = DensitySecondary,
    tertiary = DensityTertiary,
    background = DensityBackgroundLight,
    surface = DensitySurfaceLight,
    onPrimary = DensityOnPrimaryLight,
    onBackground = DensityOnSurfaceLight,
    onSurface = DensityOnSurfaceLight,
    surfaceVariant = Color(0xFFE1E2EC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // --- ARCHITECTURE BEST PRACTICE: THEME DESIGN ---
    // Extracting to an independent Theme definition allows us to enforce the custom 
    // Water Tracker visual language without relying on potentially conflicting dynamic OS colors.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
