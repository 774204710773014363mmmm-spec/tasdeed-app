package com.shawafi.tasdeed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.R

// هوية سماوية (Sky Blue) - الألوان الخارجية للتطبيق
val Green = Color(0xFF0284C7)
val GreenDark = Color(0xFF0369A1)
val GreenLight = Color(0xFF38BDF8)
val GreenPale = Color(0xFFBAE6FD)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFDC2626)
val Gray = Color(0xFF98A2B3)
val Green2 = Color(0xFF22C55E)

// التدرج السماوي - هوية التطبيق
val GreenBrush = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)))
val GreenSoftBrush = Brush.linearGradient(listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD)))

// خط عربي حديث
val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_black, FontWeight.Black)
)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = GreenLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBAE6FD),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Amber,
    onTertiary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF4B5563),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    outline = Color(0xFFD1D5DB),
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
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFD4D4D4),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    outline = Color(0xFF444444),
    error = Color(0xFFF87171)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Black, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun TasdeedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}