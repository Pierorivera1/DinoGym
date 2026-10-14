package com.dinogym.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF385540), onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EBDC), onPrimaryContainer = Color(0xFF263D2C),
    secondary = Color(0xFF385540), onSecondary = Color.White, onSecondaryContainer = Color(0xFF263D2C),
    background = Color(0xFFF9F9F3), surface = Color(0xFFF9F9F3),
    onBackground = Color(0xFF242B25),
    surfaceContainer = Color(0xFFF0F1E9), surfaceContainerLow = Color(0xFFF4F5EE),
    onSurface = Color(0xFF242B25), onSurfaceVariant = Color(0xFF687064),
    outlineVariant = Color(0xFFDFE3D7), secondaryContainer = Color(0xFFE7EBDD),
    outline = Color(0xFF818B7B), surfaceTint = Color(0xFF385540),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6D4AE), onPrimary = Color(0xFF203C29),
    primaryContainer = Color(0xFF2D4936), onPrimaryContainer = Color(0xFFDCEAD7),
    secondary = Color(0xFFB6D4AE), onSecondary = Color(0xFF203C29), onSecondaryContainer = Color(0xFFDCEAD7),
    background = Color(0xFF161A17), surface = Color(0xFF161A17),
    onBackground = Color(0xFFE8EAE2),
    surfaceContainer = Color(0xFF202620), surfaceContainerLow = Color(0xFF1B211C),
    onSurface = Color(0xFFE8EAE2), onSurfaceVariant = Color(0xFFB7BFB4),
    outlineVariant = Color(0xFF384139), secondaryContainer = Color(0xFF303C31),
    outline = Color(0xFF818B7B), surfaceTint = Color(0xFFB6D4AE),
)

@Composable
fun DinoGymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
