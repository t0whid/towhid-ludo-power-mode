package com.towhid.ludo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandGoldLight,
    onPrimary = Color(0xFF3B2700),
    secondary = BrandBlue,
    tertiary = BrandRed,
    background = BrandPaperDark,
    surface = Color(0xFF102A43),
    surfaceVariant = BrandInkLight,
    onBackground = Color(0xFFF7FBFF),
    onSurface = Color(0xFFF7FBFF),
    onSurfaceVariant = Color(0xFFB9CDDA),
    outline = Color(0xFF52738A)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandInk,
    onPrimary = Color.White,
    secondary = BrandBlue,
    tertiary = BrandGold,
    background = BrandPaper,
    surface = Color.White,
    onBackground = BrandInk,
    onSurface = BrandInk
)

@Composable
fun TowhidLudoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
