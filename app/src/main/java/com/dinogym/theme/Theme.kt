package com.dinogym.theme

import android.os.Build
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
    primary = Color(0xFFA5CFA8), onPrimary = Color(0xFF12371E),
    tertiary = Color(0xFFB8CCAA), onTertiary = Color(0xFF24351D),
    tertiaryContainer = Color(0xFF3A4C32), onTertiaryContainer = Color(0xFFD4E8C5),
    primaryContainer = Color(0xFF2B4F35), onPrimaryContainer = Color(0xFFC1ECC3),
    secondary = Color(0xFFBBCBB7), onSecondary = Color(0xFF263526),
    secondaryContainer = Color(0xFF3C4B3B), onSecondaryContainer = Color(0xFFD7E8D2),
    background = Color(0xFF181C18), surface = Color(0xFF181C18),
    onBackground = Color(0xFFE1E5DC), onSurface = Color(0xFFE1E5DC),
    surfaceContainer = Color(0xFF222722), surfaceContainerLow = Color(0xFF1C211C),
    surfaceContainerLowest = Color(0xFF101510), surfaceContainerHigh = Color(0xFF2C312B),
    surfaceContainerHighest = Color(0xFF373C36), surfaceDim = Color(0xFF101510),
    surfaceBright = Color(0xFF373C36), surfaceVariant = Color(0xFF41493F),
    onSurfaceVariant = Color(0xFFC1C9BC), outlineVariant = Color(0xFF41493F),
    outline = Color(0xFF8B9486), surfaceTint = Color(0xFFA5CFA8),
    inverseSurface = Color(0xFFE1E5DC), inverseOnSurface = Color(0xFF2C312B),
    inversePrimary = Color(0xFF385540),
)

@Composable
fun DinoGymTheme(
    darkTheme: Boolean = false,
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
