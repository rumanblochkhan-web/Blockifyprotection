package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = BluePrimary,
        onPrimary = White,
        primaryContainer = BlueLightSurface,
        onPrimaryContainer = BlueDark,
        secondary = GreenSuccess,
        onSecondary = White,
        secondaryContainer = GreenLightSurface,
        onSecondaryContainer = GreenDark,
        background = BackgroundLight,
        onBackground = TextPrimary,
        surface = CardBackground,
        onSurface = TextPrimary,
        surfaceVariant = BlueLightSurface,
        onSurfaceVariant = TextSecondary,
        outline = BorderLight,
        outlineVariant = BorderLight
    )

private val DarkColorScheme = LightColorScheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Keep clean white theme as requested in reference image
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

