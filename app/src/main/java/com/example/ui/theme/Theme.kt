package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkGlassColorScheme = darkColorScheme(
    primary = WinPrimary,
    onPrimary = Color.White,
    primaryContainer = WinPrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = WinSecondary,
    onSecondary = Color.Black,
    background = Color.Transparent, // Transparent so MainApp background shows through
    onBackground = WinOnBackground,
    surface = WinSurface.copy(alpha = 0.85f), // Acrylic glass effect
    onSurface = WinOnSurface,
    surfaceVariant = WinSurfaceVariant.copy(alpha = 0.75f),
    onSurfaceVariant = WinOnSurface,
    error = WinError,
    onError = Color.White
)

private val LightGlassColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7), // Sky Blue 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE), // Sky 100
    onPrimaryContainer = Color(0xFF0369A1), // Sky 700
    secondary = Color(0xFF4F46E5), // Indigo 600
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFFFFFFFF).copy(alpha = 0.92f), // Clean white glass
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF0F9FF), // Sky 50
    onSurfaceVariant = Color(0xFF334155), // Slate 700
    outline = Color(0xFFBAE6FD), // Sky 200
    error = WinError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGlassColorScheme else LightGlassColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
