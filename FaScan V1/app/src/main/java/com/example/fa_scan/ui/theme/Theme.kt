package com.example.fa_scan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GreenButton,
    onPrimary = Color.White,
    primaryContainer = GreenDarkShade,
    onPrimaryContainer = GreenLighterShade,
    secondary = GreenMediumShade,
    onSecondary = Color.White,
    background = DarkSurface,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceContainer = Color(0xFF132B14),
    surfaceContainerHigh = Color(0xFF1B381D)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenButton,              // 265728 for buttons
    onPrimary = Color.White,
    primaryContainer = GreenLighterShade, // Lighter shade tint
    onPrimaryContainer = GreenDarkShade,  // 0A1C0B for text/icons
    secondary = GreenMediumShade,
    onSecondary = Color.White,
    background = LightSurface,
    onBackground = GreenDarkShade,
    surface = LightSurface,
    onSurface = GreenDarkShade,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDCE8DC)
)

@Composable
fun FaScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false by default to enforce user's custom green theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
