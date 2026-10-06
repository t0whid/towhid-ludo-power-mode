package com.towhid.ludo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandGoldLight,
    onPrimary = BrandInk,
    secondary = Color(0xFF8FB9FF),
    tertiary = BrandGold,
    background = BrandPaperDark,
    surface = BrandPaperDark,
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandInk,
    onPrimary = Color.White,
    secondary = Color(0xFF2474D8),
    tertiary = BrandGold,
    background = BrandPaper,
    surface = BrandPaper,
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
