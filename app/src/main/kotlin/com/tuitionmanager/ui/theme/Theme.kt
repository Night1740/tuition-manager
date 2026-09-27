package com.tuitionmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF14202B)
private val Paper = Color(0xFFF4F6F8)
private val DeepBlue = Color(0xFF0E3A5D)
private val ErrorRed = Color(0xFF9F1D1D)

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F2),
    onPrimaryContainer = Color(0xFF062033),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF5C6B7A),
)

private val NightInk = Color(0xFFE7EEF4)
private val NightPaper = Color(0xFF101418)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7E8),
    onPrimary = Color(0xFF062033),
    primaryContainer = Color(0xFF1C4E73),
    onPrimaryContainer = Color(0xFFD6E6F2),
    background = NightPaper,
    onBackground = NightInk,
    surface = Color(0xFF1A2128),
    onSurface = NightInk,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF8B9AAB),
)

private val TuitionTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val TuitionShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun TuitionManagerTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = TuitionTypography,
        shapes = TuitionShapes,
        content = content,
    )
}
