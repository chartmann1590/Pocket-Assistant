package com.charles.pocketassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2F6FED),
    secondary = Color(0xFF4E5E8E),
    tertiary = Color(0xFF006875)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB5C4FF),
    secondary = Color(0xFFC0C6E9),
    tertiary = Color(0xFF84D2E3)
)

@Composable
fun PocketAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
