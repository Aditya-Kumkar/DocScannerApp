package com.example.docscanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = ButterGold,
    onPrimary = Color.White,
    primaryContainer = ButterContainerLight,
    onPrimaryContainer = OnButterContainerLight,
    secondary = ButterYellow,
    onSecondary = Color.Black,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    outline = CardBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = ButterYellowDark,
    onPrimary = Color(0xFF281900),
    primaryContainer = ButterContainerDark,
    onPrimaryContainer = OnButterContainerDark,
    secondary = ButterYellowDark,
    onSecondary = Color.Black,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    outline = CardBorderDark
)

@Composable
fun PdfButterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
