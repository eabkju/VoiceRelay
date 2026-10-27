package com.voicerelay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = DeepCyan,
    secondary = NeonPurple,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    error = RedPeak
)

private val LightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = Color.White,
    secondary = NeonPurple,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightCard,
    error = RedPeak
)

@Composable
fun VoiceRelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
