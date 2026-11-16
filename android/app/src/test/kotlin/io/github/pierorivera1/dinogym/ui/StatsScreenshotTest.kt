package io.github.pierorivera1.dinogym.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import io.github.pierorivera1.dinogym.domain.rules.ProgressRules
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.stats.EditingSet
import io.github.pierorivera1.dinogym.ui.stats.HistoryMonth
import io.github.pierorivera1.dinogym.ui.stats.HistoryRow
import io.github.pierorivera1.dinogym.ui.stats.ProgressListContent
import io.github.pierorivera1.dinogym.ui.stats.ProgressListRow
import io.github.pierorivera1.dinogym.ui.stats.ProgressListUiState
import io.github.pierorivera1.dinogym.ui.stats.ProgressScreen
import io.github.pierorivera1.dinogym.ui.stats.ProgressUiState
import io.github.pierorivera1.dinogym.ui.stats.SessionDetailActions
import io.github.pierorivera1.dinogym.ui.stats.SessionDetailScreen
import io.github.pierorivera1.dinogym.ui.stats.SessionDetailUiState
import io.github.pierorivera1.dinogym.ui.stats.SessionExercise
import io.github.pierorivera1.dinogym.ui.stats.SetChip
import io.github.pierorivera1.dinogym.ui.stats.StatsHistoryContent
import io.github.pierorivera1.dinogym.ui.stats.StatsHistoryUiState
import io.github.pierorivera1.dinogym.ui.stats.StatsScreen
import io.github.pierorivera1.dinogym.ui.stats.StatsTab
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Renders the Stats screens (history, progress tab, past session, progress view and their
 * empty states) in Night/English and Day/Spanish at the mocks' size (390×844dp), to compare
 * with `StatsHistory.dc.html`, `SessionDetail.dc.html` and `Progress.dc.html` by eye.
 * Output: app/build/outputs/roborazzi/stats_*.png. Nothing is compared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class StatsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun historyNightEnglish() = capture("stats_history_en_night", ThemeMode.NIGHT) { History(StatsSamples.history()) }

    @Test
    @Config(qualifiers = SPANISH)
    fun historyDaySpanish() = capture("stats_history_es_day", ThemeMode.DAY) { History(StatsSamples.history()) }

    @Test
    fun historyEmptyNightEnglish() = capture("stats_history_empty_en_night", ThemeMode.NIGHT) {
        History(StatsHistoryUiState(isLoading = false))
    }

    @Test
    fun progressTabNightEnglish() = capture("stats_progress_tab_en_night", ThemeMode.NIGHT) { ProgressTab(StatsSamples.progressList(spanish = false)) }

    @Test
    @Config(qualifiers = SPANISH)
    fun progressTabDaySpanish() = capture("stats_progress_tab_es_day", ThemeMode.DAY) { ProgressTab(StatsSamples.progressList(spanish = true)) }

    @Test
    fun sessionNightEnglish() = capture("stats_session_en_night", ThemeMode.NIGHT) {
        SessionDetailScreen(StatsSamples.session(spanish = false), SessionDetailActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun sessionDaySpanish() = capture("stats_session_es_day", ThemeMode.DAY) {
        SessionDetailScreen(StatsSamples.session(spanish = true), SessionDetailActions.None)
    }

    /** A weighted set open in the stepper (KG row), and the delete dialog. */
    @Test
    fun sessionWeightAndDeleteNightEnglish() = capture("stats_session_delete_en_night", ThemeMode.NIGHT) {
        SessionDetailScreen(StatsSamples.weightedSession().copy(confirmingDelete = true), SessionDetailActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun sessionWeightDaySpanish() = capture("stats_session_weight_es_day", ThemeMode.DAY) {
        SessionDetailScreen(StatsSamples.weightedSession(), SessionDetailActions.None)
    }

    @Test
    fun progressNightEnglish() = capture("stats_progress_en_night", ThemeMode.NIGHT) {
        ProgressScreen(StatsSamples.progress(spanish = false), onBack = {})
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun progressDaySpanish() = capture("stats_progress_es_day", ThemeMode.DAY) {
        ProgressScreen(StatsSamples.progress(spanish = true), onBack = {})
    }

    @Test
    fun progressWeightNightEnglish() = capture("stats_progress_weight_en_night", ThemeMode.NIGHT) {
        ProgressScreen(StatsSamples.weightedProgress(), onBack = {})
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun progressEmptyDaySpanish() = capture("stats_progress_empty_es_day", ThemeMode.DAY) {
        ProgressScreen(StatsSamples.progress(spanish = true).copy(progress = ProgressRules.progress(emptyList())), onBack = {})
    }

    @Composable
    private fun History(state: StatsHistoryUiState) {
        AppShell(tab = BottomTab.STATS, onTabClick = {}, onStartClick = {}) {
            StatsScreen(StatsTab.HISTORY, onSelectTab = {}) { StatsHistoryContent(state, onOpenSession = {}) }
        }
    }

    @Composable
    private fun ProgressTab(state: ProgressListUiState) {
        AppShell(tab = BottomTab.STATS, onTabClick = {}, onStartClick = {}) {
            StatsScreen(StatsTab.PROGRESS, onSelectTab = {}) { ProgressListContent(state, onOpenProgress = {}) }
        }
    }

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent { DinoTheme(mode) { content() } }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SPANISH = "es-w390dp-h844dp-420dpi"
    }
}

/** The data of the mocks, in English or Spanish (routine and exercise names are user data). */
private object StatsSamples {
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) =
        LocalDate.of(year, month, dayOfMonth).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Routine names are user data, so English and Spanish share it; only the device words change. */
    fun history(): StatsHistoryUiState {
        val data = listOf(
            listOf(16, "Pull", 42 * 60 + 10, 14, 1), listOf(14, "Lower", 31 * 60 + 5, 5, 0),
            listOf(13, "Upper", 48 * 60 + 30, 15, 2), listOf(11, "Legs", 29 * 60 + 40, 5, 0),
            listOf(10, "Push", 38 * 60 + 15, 11, 1), listOf(9, "Pull", 41 * 60 + 50, 14, 0),
        )
        val nov = data.map { (d, name, secs, sets, bests) ->
            HistoryRow("n$d", LocalDate.of(2026, 11, d as Int), name as String, (secs as Int).toLong(), sets as Int, bests as Int)
        }
        val oct = listOf(HistoryRow("o31", LocalDate.of(2026, 10, 31), "Upper", 47 * 60L, 15, 0))
        return StatsHistoryUiState(isLoading = false, months = listOf(HistoryMonth(YearMonth.of(2026, 11), nov), HistoryMonth(YearMonth.of(2026, 10), oct)))
    }

    fun progressList(spanish: Boolean) = ProgressListUiState(
        isLoading = false,
        rows = listOf(
            ProgressListRow("1", if (spanish) "Curl de bíceps" else "Bicep curl", ExerciseIcon.CURL, SetOneResult("s", 1, 12, null), false),
            ProgressListRow("2", if (spanish) "Fondos" else "Dips", ExerciseIcon.DIP, SetOneResult("s", 1, 8, 20.0), true),
            ProgressListRow("3", if (spanish) "Front lever + dominadas" else "Front lever + pulls", ExerciseIcon.LEVER, SetOneResult("s", 1, 4, null), false),
            ProgressListRow("4", if (spanish) "Dominadas" else "Pull-ups", ExerciseIcon.PULL_UP, SetOneResult("s", 1, 7, null), false),
            ProgressListRow("5", if (spanish) "Remo" else "Rows", ExerciseIcon.ROW, null, false),
        ),
    )

    fun session(spanish: Boolean): SessionDetailUiState {
        val names = if (spanish) {
            listOf("Front lever + dominadas", "Curl de antebrazo", "Dominadas", "Curl inverso", "Remo", "Curl de bíceps")
        } else {
            listOf("Front lever + pulls", "Forearm curl", "Pull-ups", "Reverse curl", "Rows", "Bicep curl")
        }
        val icons = listOf(ExerciseIcon.LEVER, ExerciseIcon.CURL, ExerciseIcon.PULL_UP, ExerciseIcon.CURL, ExerciseIcon.ROW, ExerciseIcon.CURL)
        val reps = listOf(listOf(4, 4), listOf(13, 13, 13), listOf(7, 6), listOf(25, 25), listOf(12, 12, 12), listOf(12, 12))
        val exercises = names.indices.map { i ->
            SessionExercise(
                exerciseId = "e$i",
                name = names[i],
                icon = icons[i],
                template = if (i == 0) (if (spanish) "3s + {n} dominadas + 3s" else "3s + {n} pulls + 3s") else null,
                isNewBest = i == 2,
                sets = reps[i].mapIndexed { j, r -> SetChip("e$i-$j", j + 1, r, null) },
            )
        }
        return SessionDetailUiState(
            isLoading = false,
            routineName = "Pull",
            date = LocalDate.of(2026, 11, 16),
            durationSeconds = 42 * 60 + 10L,
            setCount = 14,
            exercises = exercises,
            editing = EditingSet("e2-1", "e2", setIndex = 2, setCount = 2, reps = 6, weightKg = null),
        )
    }

    fun weightedSession() = SessionDetailUiState(
        isLoading = false,
        routineName = "Push",
        date = LocalDate.of(2026, 11, 14),
        durationSeconds = 51 * 60L,
        setCount = 9,
        exercises = listOf(
            SessionExercise("a", "Machine chest press", ExerciseIcon.PUSH_UP, null, true, listOf(SetChip("a1", 1, 8, 50.0), SetChip("a2", 2, 7, 50.0), SetChip("a3", 3, 7, 50.0))),
            SessionExercise("b", "Dumbbell lateral raise", ExerciseIcon.RAISE, null, false, (1..4).map { SetChip("b$it", it, 12, 10.0) }),
            SessionExercise("c", "Skullcrusher", ExerciseIcon.GENERIC, null, false, listOf(SetChip("c1", 1, 10, 22.5), SetChip("c2", 2, 9, 22.5))),
        ),
        editing = EditingSet("a1", "a", setIndex = 1, setCount = 3, reps = 8, weightKg = 52.5),
    )

    fun progress(spanish: Boolean) = ProgressUiState(
        isLoading = false,
        name = if (spanish) "Dominadas" else "Pull-ups",
        icon = ExerciseIcon.PULL_UP,
        progress = ProgressRules.progress(
            listOf(
                SetOneResult("a", day(7, 7), 4, null), SetOneResult("b", day(7, 20), 4, null),
                SetOneResult("c", day(8, 4), 5, null), SetOneResult("d", day(9, 15), 6, null),
                SetOneResult("e", day(10, 2), 5, null), SetOneResult("f", day(11, 16), 7, null),
            ),
        ),
        today = LocalDate.of(2026, 11, 16),
        zone = ZoneOffset.UTC,
    )

    fun weightedProgress() = ProgressUiState(
        isLoading = false,
        name = "Machine chest press",
        icon = ExerciseIcon.PUSH_UP,
        progress = ProgressRules.progress(
            listOf(
                SetOneResult("a", day(12, 3, 2025), 8, 45.0), SetOneResult("b", day(1, 14), 10, 45.0),
                SetOneResult("c", day(3, 2), 6, 47.5), SetOneResult("d", day(6, 9), 9, 47.5),
                SetOneResult("e", day(9, 1), 7, 50.0), SetOneResult("f", day(11, 14), 8, 50.0),
            ),
        ),
        today = LocalDate.of(2026, 11, 16),
        zone = ZoneOffset.UTC,
    )
}
