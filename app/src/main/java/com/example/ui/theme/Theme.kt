package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HappDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001A22),
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = Color(0xFF99F6FF),
    secondary = NeonEmerald,
    onSecondary = Color(0xFF00220F),
    secondaryContainer = NeonEmeraldDark,
    onSecondaryContainer = Color(0xFF8CFAC0),
    tertiary = CyberPurple,
    onTertiary = Color.White,
    tertiaryContainer = CyberPurpleDark,
    onTertiaryContainer = Color(0xFFE9D5FF),
    background = HappBgDark,
    onBackground = TextPrimary,
    surface = HappSurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = HappCardDark,
    onSurfaceVariant = TextSecondary,
    outline = HappCardBorder,
    outlineVariant = Color(0xFF334155),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Happ & Incy are designed as sleek dark-mode clients
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HappDarkColorScheme,
        typography = Typography,
        content = content
    )
}
