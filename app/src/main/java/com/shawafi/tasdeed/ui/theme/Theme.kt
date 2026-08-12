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

val Green = Color(0xFF059669)
val GreenDark = Color(0xFF047857)
val GreenLight = Color(0xFF10B981)
val GreenPale = Color(0xFFD1FAE5)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFDC2626)
val Gray = Color(0xFF98A2B3)

// التدرج الأخضر - هوية التطبيق
val GreenBrush = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF047857)))
val GreenSoftBrush = Brush.linearGradient(listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)))

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenPale,
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = GreenLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F3D0),
    onSecondaryContainer = Color(0xFF064E3B),
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
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF052E22),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = GreenLight,
    onSecondary = Color(0xFF052E22),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFA7F3D0),
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TasdeedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
