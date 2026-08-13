package com.shawafi.tasdeed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// هوية سماوية (Sky Blue) - الألوان الخارجية للتطبيق
val Green = Color(0xFF0284C7)
val GreenDark = Color(0xFF0369A1)
val GreenLight = Color(0xFF38BDF8)
val GreenPale = Color(0xFFBAE6FD)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFDC2626)
val Gray = Color(0xFF98A2B3)

// التدرج السماوي - هوية التطبيق
val GreenBrush = Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF0369A1)))
val GreenSoftBrush = Brush.linearGradient(listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD)))

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenPale,
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = GreenLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBAE6FD),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Amber,
    onTertiary = Color.White,
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    error = Red
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = GreenLight,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Amber,
    onTertiary = Color(0xFF1F2937),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF9CA3AF),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF3F4F6),
    error = Color(0xFFF87171)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun TasdeedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
