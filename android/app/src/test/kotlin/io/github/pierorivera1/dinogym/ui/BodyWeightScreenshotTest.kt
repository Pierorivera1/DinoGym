package io.github.pierorivera1.dinogym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.domain.model.WeighIn
import io.github.pierorivera1.dinogym.domain.rules.WeightRange
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightActions
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightPane
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightStateMapper
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightUiState
import io.github.pierorivera1.dinogym.ui.bodyweight.WeightSheet
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Renders the WEIGHT segment of Stats (task 11) with its sheets, in Night/English and
 * Day/Spanish at the mocks' size (390×844dp), to compare with `BodyWeight.dc.html` by eye.
 * The STATS title and the HISTORY / PROGRESS / WEIGHT control are drawn here as a stand-in
 * for the Stats screen, which hosts the segment. Output: app/build/outputs/roborazzi/bodyweight_*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class BodyWeightScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun weightNightEnglish() = capture("bodyweight_en_night", ThemeMode.NIGHT) { Stats(english = true, WeightSamples.state()) }

    @Test
    @Config(qualifiers = SPANISH)
    fun weightDaySpanish() = capture("bodyweight_es_day", ThemeMode.DAY) { Stats(english = false, WeightSamples.state()) }

    @Test
    fun logSheetNightEnglish() = capture("bodyweight_log_en_night", ThemeMode.NIGHT) {
        Stats(english = true, WeightSamples.state().copy(sheet = WeightSheet.Log(WeightSamples.TODAY, WeightSamples.TODAY, "78.3")))
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun goalSheetDaySpanish() = capture("bodyweight_goal_es_day", ThemeMode.DAY) {
        Stats(english = false, WeightSamples.state().copy(sheet = WeightSheet.Goal("76,0", hasGoal = true)))
    }

    /** A past day (forward is allowed) and a value that does not parse. */
    @Test
    fun logSheetPastDayWithErrorNightEnglish() = capture("bodyweight_log_error_en_night", ThemeMode.NIGHT) {
        val day = WeightSamples.TODAY.minusDays(2)
        Stats(english = true, WeightSamples.state().copy(sheet = WeightSheet.Log(day, WeightSamples.TODAY, "7.8.3", showError = true)))
    }

    /** One month, no goal, and the UNDO bar after a delete. */
    @Test
    @Config(qualifiers = SPANISH)
    fun oneMonthNoGoalUndoDaySpanish() = capture("bodyweight_1m_undo_es_day", ThemeMode.DAY) {
        val state = WeightSamples.state(goal = null, range = WeightRange.ONE_MONTH)
        Stats(english = false, state.copy(undo = state.weighIns.first()))
    }

    @Test
    fun emptyNightEnglish() = capture("bodyweight_empty_en_night", ThemeMode.NIGHT) {
        Stats(english = true, WeightSamples.empty())
    }

    @Composable
    private fun Stats(english: Boolean, state: BodyWeightUiState) {
        AppShell(tab = BottomTab.STATS, onTabClick = {}, onStartClick = {}) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
                        Text("STATS", style = DinoTheme.type.title.copy(fontSize = 26.sp), color = DinoTheme.colors.outline)
                    }
                    SegmentedControl(
                        options = if (english) listOf("HISTORY", "PROGRESS", "WEIGHT") else listOf("HISTORIAL", "PROGRESO", "PESO"),
                        selectedIndex = 2,
                        onSelect = {},
                        asTabs = true,
                    )
                }
                BodyWeightPane(state, BodyWeightActions.None, Modifier.weight(1f))
            }
        }
    }

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent { DinoTheme(mode) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SPANISH = "es-w390dp-h844dp-420dpi"
    }
}

/** The mock's sample data: three months of weigh-ins trending down, from the same seeded generator. */
private object WeightSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 16)
    private val zone = ZoneOffset.UTC

    fun weighIns(): List<WeighIn> {
        var seed = 7L
        fun rnd(): Double {
            seed = (seed * 16807) % 2147483647
            return seed / 2147483647.0
        }
        val days = 90
        val list = mutableListOf<WeighIn>()
        for (d in 0..days) {
            if (d == days - 1) continue
            if (d < days - 3 && rnd() < 0.42) continue
            val kg = if (d == days) 78.3 else 80.6 - 2.1 * (d.toDouble() / days) + (rnd() - 0.5) * 1.1
            val at = TODAY.minusDays((days - d).toLong()).atTime(7, 30).toInstant(zone).toEpochMilli()
            list += WeighIn("w$d", at, Math.round(kg * 10) / 10.0)
        }
        return list.sortedByDescending { it.measuredAt }
    }

    fun state(goal: Double? = 76.0, range: WeightRange = WeightRange.THREE_MONTHS) =
        BodyWeightStateMapper.map(weighIns(), goal, range, TODAY, zone)

    fun empty() = BodyWeightStateMapper.map(emptyList(), null, WeightRange.THREE_MONTHS, TODAY, zone)
}
