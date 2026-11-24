package com.abhimanyu.kiapartslocator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightSurface2,
    onBackground = LightText,
    onSurface = LightText,
    primary = Accent,
    onPrimary = OnAccentLight,
    error = ErrorLight
)

private val DarkColors = darkColorScheme(
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurface2,
    onBackground = DarkText,
    onSurface = DarkText,
    primary = AccentDark,
    onPrimary = OnAccentDark,
    error = ErrorDark
)

@Composable
fun PartsPickerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = PartsPickerTypography, content = content)
}
