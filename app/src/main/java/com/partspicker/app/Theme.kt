package com.partspicker.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val onInk: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val error: Color,
    val success: Color,
    val scrim: Color
)

val LightColors = AppColors(
    bg = Color(0xFFF0ECE4),
    surface = Color(0xFFFAF8F3),
    surface2 = Color(0xFFE8E3D9),
    ink = Color(0xFF171510),
    onInk = Color(0xFFF4F0E8),
    text = Color(0xFF171510),
    muted = Color(0xFF7A7467),
    line = Color(0xFFE4DFD3),
    accent = Color(0xFFD9482B),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFB3321F),
    success = Color(0xFF2F7A4B),
    scrim = Color(0x80171510)
)

// Raised vs. the original near-black values — bg/surface/surface2 were too
// close in luminance to read as separate layers (and caused banding/dither
// on low-brightness OLED screens). Each step is now a clearly visible jump.
val DarkColors = AppColors(
    bg = Color(0xFF141311),
    surface = Color(0xFF252019),
    surface2 = Color(0xFF362F24),
    ink = Color(0xFFF2EEE6),
    onInk = Color(0xFF15130F),
    text = Color(0xFFF2EEE6),
    muted = Color(0xFFAFA696),
    line = Color(0xFF4A4134),
    accent = Color(0xFFFF6F4A),
    onAccent = Color(0xFF1A0D08),
    error = Color(0xFFFF8A77),
    success = Color(0xFF6FD39A),
    scrim = Color(0xA6000000)
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.bg, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text, error = colors.error
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.bg, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text, error = colors.error
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalContentColor provides colors.text
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
