package io.github.pierorivera1.dinogym.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.BottomBar
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.components.Cursor
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.Mascot
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.StatBar
import io.github.pierorivera1.dinogym.ui.components.Stepper
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import androidx.compose.ui.draw.clip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every component (and the app shell) to PNGs on the JVM, so the UI can be checked
 * against the mocks without an emulator. Output: app/build/outputs/roborazzi/.
 * Nothing is compared automatically yet; the images are for a person to look at.
 *
 * 420dpi is a common real phone density (2.625x), where pixel art blurs unless it is
 * snapped to whole screen pixels, so it is the harder case to check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one opens the database, whose native SQLite library is not available on the JVM.
@Config(sdk = [36], qualifiers = "w400dp-h1100dp-420dpi", application = android.app.Application::class)
class ComponentGalleryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun galleryNight() = capture("gallery_night") { DinoTheme(ThemeMode.NIGHT) { Gallery() } }

    @Test
    fun galleryDay() = capture("gallery_day") { DinoTheme(ThemeMode.DAY) { Gallery() } }

    @Test
    @Config(qualifiers = "en-w360dp-h740dp-420dpi")
    fun shellEnglishNight() = capture("shell_en_night") { DinoTheme(ThemeMode.NIGHT) { DinoGymNavigation() } }

    @Test
    @Config(qualifiers = "es-w360dp-h740dp-420dpi")
    fun shellSpanishDay() = capture("shell_es_day") { DinoTheme(ThemeMode.DAY) { DinoGymNavigation() } }

    /** EJERCICIOS must still fit on one line when the user picks a large system font. */
    @Test
    @Config(qualifiers = "es-w360dp-h740dp-420dpi")
    fun shellSpanishLargeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        capture("shell_es_day_font130") { DinoTheme(ThemeMode.DAY) { DinoGymNavigation() } }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        // Let the text box finish typing (it runs on the test clock).
        compose.mainClock.advanceTimeBy(10_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

@Composable
private fun Gallery() {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (colors.isNight) "NIGHT" else "DAY", style = DinoTheme.type.title, color = colors.outline)
        Text("Neutral-grip pull-ups", style = DinoTheme.type.heading, color = colors.outline)
        Text("Stop most sets 1–2 reps short of failure. ÁÉÍÓÚÜÑ ¿¡ áéíóúüñ", style = DinoTheme.type.body, color = colors.outline)
        Text("REPS · KG · REST · DESCANSO", style = DinoTheme.type.label, color = colors.muted)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(stringResource(R.string.action_log_set), {}, icon = R.drawable.ic_px_check)
            SecondaryButton(stringResource(R.string.action_skip), {})
            DangerIconButton(stringResource(R.string.cd_end_workout), {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("2:30", {}, icon = R.drawable.ic_px_timer)
            PrimaryButton("START PULL", {}, Modifier.weight(1f), icon = R.drawable.ic_px_start)
        }

        TextBox("You feel stronger. New best on pull-ups: 7 reps.", showMascot = true)
        TextBox("Breathe. Up next: pull-ups, set 2 of 2.")

        StatBar(stringResource(R.string.label_exercise), total = 6, filled = 2, value = "3/6", markCurrent = true)
        StatBar(stringResource(R.string.label_rest), total = 10, filled = 7, value = "1:48")

        // The active set card, built from the theme pieces, to check shadow + stepped + sel colours.
        Column(
            Modifier
                .hardShadow(colors.shadow, SteppedShape)
                .clip(SteppedShape)
                .background(colors.selBg)
                .steppedBorder(colors.selBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Cursor(scale = 3)
                Text("SET 2 OF 2", style = DinoTheme.type.button, color = colors.selText)
            }
            Stepper(
                label = stringResource(R.string.label_reps),
                value = 6,
                onDecrease = {},
                onIncrease = {},
                decreaseDescription = stringResource(R.string.cd_one_less_rep),
                increaseDescription = stringResource(R.string.cd_one_more_rep),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(size = 64.dp)
            Cursor(scale = 4)
            Cursor(scale = 2)
            // The launcher icon as a circular launcher would mask it (72dp of the 108dp canvas).
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF0C0915)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(108.dp))
            }
        }
        val icons = listOf(
            R.drawable.ic_px_home, R.drawable.ic_px_programs, R.drawable.ic_px_start, R.drawable.ic_px_stats,
            R.drawable.ic_px_exercises, R.drawable.ic_px_check, R.drawable.ic_px_timer, R.drawable.ic_px_plus,
            R.drawable.ic_px_minus, R.drawable.ic_px_close, R.drawable.ic_px_edit, R.drawable.ic_px_skip,
            R.drawable.ic_px_pull,
        )
        icons.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { icon ->
                    Box(
                        Modifier.size(44.dp).clip(SteppedShape).background(colors.surface2),
                        contentAlignment = Alignment.Center,
                    ) { PixelIcon(icon, colors.outline) }
                }
            }
        }

        Box(Modifier.padding(top = 16.dp)) {
            BottomBar(selected = BottomTab.HOME, onTabClick = {}, onStartClick = {})
        }
    }
}
