package io.github.pierorivera1.dinogym.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Which palette to use. SYSTEM follows the phone's dark mode: dark is Night, light is Day. */
enum class ThemeMode { SYSTEM, NIGHT, DAY }

private val LocalDinoColors = staticCompositionLocalOf { NightColors }
private val LocalDinoType = staticCompositionLocalOf { DinoTypeDefault }

/**
 * The app theme. Provides [DinoColors] and [DinoType] to everything inside it, and fills
 * M3's colour scheme and typography from the same tokens so stock M3 components match.
 * There is no dynamic (wallpaper) colour: the pixel style needs its own palette.
 */
@Composable
fun DinoTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val night = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.NIGHT -> true
        ThemeMode.DAY -> false
    }
    val colors = if (night) NightColors else DayColors
    val type = DinoTypeDefault

    // Status and navigation bar icons: light icons on Night, dark icons on Day.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
        }
    }

    CompositionLocalProvider(
        LocalDinoColors provides colors,
        LocalDinoType provides type,
    ) {
        MaterialTheme(
            colorScheme = materialColors(colors),
            typography = Typography(
                displayLarge = type.numberXl,
                headlineMedium = type.title,
                titleLarge = type.heading,
                bodyLarge = type.body,
                bodyMedium = type.body,
                labelLarge = type.button,
                labelMedium = type.label,
            ),
            content = content,
        )
    }
}

/** Access point for screens: `DinoTheme.colors.primary`, `DinoTheme.type.title`. */
object DinoTheme {
    val colors: DinoColors
        @Composable @ReadOnlyComposable get() = LocalDinoColors.current

    val type: DinoType
        @Composable @ReadOnlyComposable get() = LocalDinoType.current
}

/** Maps our tokens onto M3 roles so stock components (dialogs, text fields) look right. */
private fun materialColors(c: DinoColors) =
    // Start from M3's dark or light defaults for the roles we do not set, then override.
    (if (c.isNight) darkColorScheme() else lightColorScheme()).copy(
        primary = c.primary, onPrimary = c.onPrimary,
        secondary = c.select, onSecondary = c.onSelect,
        tertiary = c.action, onTertiary = c.bg,
        background = c.bg, onBackground = c.outline,
        surface = c.surface, onSurface = c.outline,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface2,
        outline = c.outline, outlineVariant = c.surface2,
        error = c.danger, onError = c.bg,
        scrim = c.scrim,
    )
