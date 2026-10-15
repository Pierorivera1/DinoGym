package com.dinogym.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF385540), onPrimary = Color.White,
    tertiary = Color(0xFF385540), onTertiary = Color.White,
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
    primary = Color(0xFF82ABFF), onPrimary = Color(0xFF082655),
    tertiary = Color(0xFF2B66D9), onTertiary = Color.White,
    primaryContainer = Color(0xFF1D3358), onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF82ABFF), onSecondary = Color(0xFF082655),
    secondaryContainer = Color(0xFF26334B), onSecondaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF202020), surface = Color(0xFF202020),
    onBackground = Color(0xFFF0F0F2), onSurface = Color(0xFFF0F0F2),
    surfaceContainer = Color(0xFF292929), surfaceContainerLow = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFB0B2BA), outlineVariant = Color(0xFF383B43),
    outline = Color(0xFF858A96), surfaceTint = Color(0xFF82ABFF),
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
