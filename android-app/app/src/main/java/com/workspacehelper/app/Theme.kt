package com.workspacehelper.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Semantic accents (not part of the Material scheme).
val Green = Color(0xFF34D399)
val GreenText = Color(0xFF6EE7B7)
val Rose = Color(0xFFFB7185)
val Amber = Color(0xFFFBBF24)
val Violet2 = Color(0xFFA99BFF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C6CF6),
    onPrimary = Color.White,
    secondary = Color(0xFF22D3EE),
    background = Color(0xFF0D0F14),
    onBackground = Color(0xFFF4F6FA),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFF1F232C),
    onSurfaceVariant = Color(0xFF9AA3B2),
    error = Rose,
    outline = Color(0xFF2A2F3B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D5EF0),
    onPrimary = Color.White,
    secondary = Color(0xFF0891B2),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF141821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141821),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5B6472),
    error = Color(0xFFE11D48),
    outline = Color(0xFFE3E7EE),
)

@Composable
fun WhTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
