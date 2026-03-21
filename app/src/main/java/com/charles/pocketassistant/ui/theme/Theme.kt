package com.charles.pocketassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF3366FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF001552),
    secondary = Color(0xFF585E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F5EC),
    onTertiaryContainer = Color(0xFF002019),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF757680),
    outlineVariant = Color(0xFFC5C5D0),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002683),
    primaryContainer = Color(0xFF1548B8),
    onPrimaryContainer = Color(0xFFDBE1FF),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFF8BD8D0),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFA7F5EC),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C5D0),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3036)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val bill: Color,
    val message: Color,
    val appointment: Color,
    val note: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = Color(0xFF2E7D32),
        onSuccess = Color.White,
        successContainer = Color(0xFFCBF5CB),
        warning = Color(0xFFE65100),
        onWarning = Color.White,
        warningContainer = Color(0xFFFFE0B2),
        bill = Color(0xFFE53935),
        message = Color(0xFF1E88E5),
        appointment = Color(0xFF8E24AA),
        note = Color(0xFF43A047)
    )
}

@Composable
fun PocketAssistantTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val extendedColors = if (isDark) {
        ExtendedColors(
            success = Color(0xFF81C784),
            onSuccess = Color(0xFF003300),
            successContainer = Color(0xFF1B5E20),
            warning = Color(0xFFFFB74D),
            onWarning = Color(0xFF3E2700),
            warningContainer = Color(0xFF5D3F00),
            bill = Color(0xFFEF9A9A),
            message = Color(0xFF90CAF9),
            appointment = Color(0xFFCE93D8),
            note = Color(0xFFA5D6A7)
        )
    } else {
        ExtendedColors(
            success = Color(0xFF2E7D32),
            onSuccess = Color.White,
            successContainer = Color(0xFFCBF5CB),
            warning = Color(0xFFE65100),
            onWarning = Color.White,
            warningContainer = Color(0xFFFFE0B2),
            bill = Color(0xFFE53935),
            message = Color(0xFF1E88E5),
            appointment = Color(0xFF8E24AA),
            note = Color(0xFF43A047)
        )
    }
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (isDark) Dark else Light,
            typography = AppTypography,
            content = content
        )
    }
}
