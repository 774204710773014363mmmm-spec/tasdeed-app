package com.shawafi.tasdeed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF059669)
val GreenDark = Color(0xFF047857)
val GreenLight = Color(0xFF10B981)
val Amber = Color(0xFFD4A843)
val Red = Color(0xFFDC2626)
val Gray = Color(0xFF98A2B3)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = GreenLight,
    onSecondary = Color.White,
    tertiary = Amber,
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF1A202C),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A202C),
    error = Red
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF052E22),
    secondary = Green,
    onSecondary = Color.White,
    tertiary = Amber,
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF3F4F6),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF3F4F6),
    error = Color(0xFFF87171)
)

@Composable
fun TasdeedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
