package com.vineyard.aivideostudio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = TextPrimary,
    primaryContainer = VioletPrimaryVariant,
    onPrimaryContainer = TextPrimary,
    secondary = AmberAccent,
    onSecondary = StudioDarkBg,
    secondaryContainer = StudioSurfaceElevated,
    onSecondaryContainer = AmberGlow,
    tertiary = CyanInfo,
    background = StudioDarkBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioCardBg,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = RoseError,
    onError = TextPrimary
)

@Composable
fun EditoraTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = StudioColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
