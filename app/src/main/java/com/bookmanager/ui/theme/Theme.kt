package com.bookmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared spacing tokens for Compose UI.
 */
object Spacing {
    val xs = 3.dp
    val sm = 5.dp
    val md = 8.dp
    val lg = 10.dp
    val xl = 14.dp
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF163D2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBE1),
    onPrimaryContainer = Color(0xFF0D2A1C),
    secondary = Color(0xFF6F5A3D),
    secondaryContainer = Color(0xFFECE1D0),
    tertiary = Color(0xFFB45F2A),
    tertiaryContainer = Color(0xFFF6D9C3),
    background = Color(0xFFF5F1E8),
    surface = Color(0xFFFFFCF5),
    surfaceVariant = Color(0xFFEAE3D6),
    outline = Color(0xFFCFC4B3),
)

private val CompactTypography = Typography(
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

/**
 * Material 3 theme for the app.
 */
@Composable
fun BookManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = CompactTypography,
        shapes = AppShapes,
        content = content,
    )
}
