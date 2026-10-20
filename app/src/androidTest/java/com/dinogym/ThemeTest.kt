package com.dinogym

import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import com.dinogym.theme.DinoGymTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultIsLightEvenWithDarkSystemConfiguration() {
        var background = Color.Unspecified
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                DinoGymTheme { background = MaterialTheme.colorScheme.background }
            }
        }
        compose.runOnIdle { assertEquals(Color(0xFFF9F9F3), background) }
    }

    @Test fun darkThemeUsesGreenAccentsWithReadableText() {
        lateinit var colors: ColorScheme
        compose.setContent { DinoGymTheme(darkTheme = true) { colors = MaterialTheme.colorScheme } }
        compose.runOnIdle {
            assertTrue(colors.primary.green > colors.primary.red)
            assertTrue(colors.primary.green > colors.primary.blue)
            listOf(
                colors.primary to colors.onPrimary,
                colors.primaryContainer to colors.onPrimaryContainer,
                colors.secondary to colors.onSecondary,
                colors.secondaryContainer to colors.onSecondaryContainer,
                colors.tertiary to colors.onTertiary,
                colors.tertiaryContainer to colors.onTertiaryContainer,
                colors.background to colors.onBackground,
                colors.surface to colors.onSurface,
                colors.surfaceContainerHighest to colors.onSurfaceVariant,
                colors.inverseSurface to colors.inverseOnSurface,
            ).forEach { (background, foreground) ->
                val contrast = (maxOf(background.luminance(), foreground.luminance()) + 0.05f) /
                    (minOf(background.luminance(), foreground.luminance()) + 0.05f)
                assertTrue("Text contrast $contrast must be at least 4.5:1", contrast >= 4.5f)
            }
        }
    }
}
