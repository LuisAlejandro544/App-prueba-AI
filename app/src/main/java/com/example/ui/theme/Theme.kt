package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyanPrimaryDark,
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFBBE9FF),
    secondary = CyberIndigoSecondaryDark,
    onSecondary = Color(0xFF1E1E60),
    secondaryContainer = Color(0xFF333380),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = CyberEmeraldTertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
    primary = CyberCyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E2F),
    secondary = CyberIndigoSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0FF),
    onSecondaryContainer = Color(0xFF0A0A4B),
    tertiary = CyberEmeraldTertiaryLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep intentional cybersecurity branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
