package io.github.pierorivera1.dinogym.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.home.CycleCell
import io.github.pierorivera1.dinogym.ui.home.HomeExerciseRow
import io.github.pierorivera1.dinogym.ui.home.HomeNext
import io.github.pierorivera1.dinogym.ui.home.HomeProgram
import io.github.pierorivera1.dinogym.ui.home.HomeScreen
import io.github.pierorivera1.dinogym.ui.home.HomeUiState
import io.github.pierorivera1.dinogym.ui.home.LastSetOne
import io.github.pierorivera1.dinogym.ui.home.LastWorkout
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import io.github.pierorivera1.dinogym.ui.workout.ActiveExerciseUi
import io.github.pierorivera1.dinogym.ui.workout.DoneSetUi
import io.github.pierorivera1.dinogym.ui.workout.NextExerciseUi
import io.github.pierorivera1.dinogym.ui.workout.RestActions
import io.github.pierorivera1.dinogym.ui.workout.RestScreen
import io.github.pierorivera1.dinogym.ui.workout.RestUiState
import io.github.pierorivera1.dinogym.ui.workout.SummaryBest
import io.github.pierorivera1.dinogym.ui.workout.SummaryRow
import io.github.pierorivera1.dinogym.ui.workout.SummaryScreen
import io.github.pierorivera1.dinogym.ui.workout.SummaryUiState
import io.github.pierorivera1.dinogym.ui.workout.WorkoutActions
import io.github.pierorivera1.dinogym.ui.workout.WorkoutScreen
import io.github.pierorivera1.dinogym.ui.workout.WorkoutSheet
import io.github.pierorivera1.dinogym.ui.workout.WorkoutUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders the workout flow screens (Home, active exercise, rest picker, rest timer, summary)
 * with the data of the approved mocks, in Night/English and Day/Spanish at the mocks' size
 * (390×844dp), so they can be compared with `docs/design/mocks/` by eye.
 * Output: app/build/outputs/roborazzi/flow_*.png. Nothing is compared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one opens the database, whose native SQLite library is not available on the JVM.
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class WorkoutFlowScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeNightEnglish() = capture("flow_home_en_night", ThemeMode.NIGHT) { Home(SampleData.home(spanish = false)) }

    @Test
    @Config(qualifiers = SPANISH)
    fun homeDaySpanish() = capture("flow_home_es_day", ThemeMode.DAY) { Home(SampleData.home(spanish = true)) }

    /** EJERCICIOS in the bar must still fit on one line with a large system font. */
    @Test
    @Config(qualifiers = SPANISH)
    fun homeDaySpanishLargeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        capture("flow_home_es_day_font130", ThemeMode.DAY) { Home(SampleData.home(spanish = true)) }
    }

    @Test
    fun homeRestDayNightEnglish() = capture("flow_home_rest_en_night", ThemeMode.NIGHT) { Home(SampleData.homeRestDay(spanish = false)) }

    @Test
    @Config(qualifiers = SPANISH)
    fun homeRestDayDaySpanish() = capture("flow_home_rest_es_day", ThemeMode.DAY) { Home(SampleData.homeRestDay(spanish = true)) }

    @Test
    fun homeEmptyNightEnglish() = capture("flow_home_empty_en_night", ThemeMode.NIGHT) {
        Home(HomeUiState(today = LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun workoutNightEnglish() = capture("flow_workout_en_night", ThemeMode.NIGHT) {
        WorkoutScreen(SampleData.workout(spanish = false), WorkoutActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun workoutDaySpanish() = capture("flow_workout_es_day", ThemeMode.DAY) {
        WorkoutScreen(SampleData.workout(spanish = true), WorkoutActions.None)
    }

    @Test
    fun restPickerNightEnglish() = capture("flow_rest_picker_en_night", ThemeMode.NIGHT) {
        WorkoutScreen(SampleData.workout(spanish = false).copy(sheet = WorkoutSheet.RestPicker(150)), WorkoutActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun restPickerDaySpanish() = capture("flow_rest_picker_es_day", ThemeMode.DAY) {
        WorkoutScreen(SampleData.workout(spanish = true).copy(sheet = WorkoutSheet.RestPicker(150)), WorkoutActions.None)
    }

    @Test
    fun endSheetNightEnglish() = capture("flow_end_en_night", ThemeMode.NIGHT) {
        WorkoutScreen(SampleData.workout(spanish = false).copy(sheet = WorkoutSheet.End), WorkoutActions.None)
    }

    @Test
    fun restNightEnglish() = capture("flow_rest_en_night", ThemeMode.NIGHT) { RestScreen(SampleData.rest(spanish = false), RestActions.None) }

    @Test
    @Config(qualifiers = SPANISH)
    fun restDaySpanish() = capture("flow_rest_es_day", ThemeMode.DAY) { RestScreen(SampleData.rest(spanish = true), RestActions.None) }

    @Test
    fun summaryNightEnglish() = capture("flow_summary_en_night", ThemeMode.NIGHT) {
        SummaryScreen(SampleData.summary(spanish = false), onEdit = {}, onFinish = {})
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun summaryDaySpanish() = capture("flow_summary_es_day", ThemeMode.DAY) {
        SummaryScreen(SampleData.summary(spanish = true), onEdit = {}, onFinish = {})
    }

    @Composable
    private fun Home(state: HomeUiState) {
        AppShell(tab = BottomTab.HOME, onTabClick = {}, onStartClick = {}) {
            HomeScreen(state, onStart = {}, onSkip = {}, onRestDone = {}, onEditRoutine = {}, onOpenSettings = {}, onOpenHistory = {})
        }
    }

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent { DinoTheme(mode) { content() } }
        // Let the text box finish typing (it runs on the test clock).
        compose.mainClock.advanceTimeBy(10_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SPANISH = "es-w390dp-h844dp-420dpi"
    }
}

/** The data shown in the approved mocks, in English or Spanish (exercise names are user data). */
private object SampleData {
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    private fun pullRows(spanish: Boolean): List<HomeExerciseRow> {
        val names = if (spanish) {
            listOf("Front lever + dominadas", "Curl de antebrazo", "Dominadas", "Curl inverso", "Remo", "Curl de bíceps")
        } else {
            listOf("Front lever + pulls", "Forearm curl", "Pull-ups", "Reverse curl", "Rows", "Bicep curl")
        }
        val targets = listOf(2 to 4, 3 to 13, 2 to 6, 2 to 25, 3 to 12, 2 to 12)
        return names.zip(targets) { name, (sets, reps) ->
            HomeExerciseRow(
                name = name,
                kind = if (name.startsWith("Front lever")) ExerciseKind.TEMPLATE else ExerciseKind.REPS,
                sets = sets,
                repMin = reps,
                repMax = reps,
                template = null,
            )
        }
    }

    private fun cycle(current: Int) = listOf("Pull", "Push", "Legs", null, "Upper", "Lower")
        .mapIndexed { i, name -> CycleCell(name, i == current) }

    fun home(spanish: Boolean) = HomeUiState(
        today = today,
        program = HomeProgram(
            id = "p",
            name = if (spanish) "Mi programa" else "My program",
            stepNumber = 1,
            stepCount = 6,
            cycle = cycle(0),
            next = HomeNext.Workout(
                routineId = "pull",
                routineName = "Pull",
                exercises = pullRows(spanish),
                inProgress = false,
                lastSetOne = LastSetOne(if (spanish) "Dominadas" else "Pull-ups", 6),
            ),
        ),
        lastWorkout = LastWorkout("Lower", LocalDate.of(2026, 11, 14)),
    )

    fun homeRestDay(spanish: Boolean) = HomeUiState(
        today = today,
        program = HomeProgram(
            id = "p",
            name = if (spanish) "Mi programa" else "My program",
            stepNumber = 4,
            stepCount = 6,
            cycle = cycle(3),
            next = HomeNext.RestDay(followingRoutine = "Upper"),
        ),
        lastWorkout = LastWorkout("Legs", LocalDate.of(2026, 11, 15)),
    )

    fun workout(spanish: Boolean) = WorkoutUiState(
        isLoading = false,
        routineName = "Pull",
        elapsedSeconds = 18 * 60 + 42,
        position = 2,
        exerciseCount = 6,
        exercise = ActiveExerciseUi(
            name = if (spanish) "Dominadas" else "Pull-ups",
            icon = ExerciseIcon.PULL_UP,
            kind = ExerciseKind.REPS,
            template = null,
            sets = 2,
            repMin = 6,
            repMax = 6,
            restSeconds = 150,
            lastTime = listOf(6, 6),
            doneSets = listOf(DoneSetUi(id = "s1", setIndex = 1, reps = 7, weightKg = null, isNewBest = true)),
            nextSetIndex = 2,
            reps = 6,
            weightKg = null,
            preFilledFromHistory = true,
            note = "",
        ),
        next = NextExerciseUi(if (spanish) "Curl inverso" else "Reverse curl", 2, 25, 25, ExerciseKind.REPS),
        loggedSetCount = 8,
    )

    fun rest(spanish: Boolean) = RestUiState(
        isLoading = false,
        routineName = "Pull",
        elapsedSeconds = 19 * 60 + 10,
        remainingSeconds = 108,
        totalSeconds = 150,
        filledBlocks = 7,
        nextLabel = if (spanish) "Dominadas, serie 2 de 2" else "Pull-ups, set 2 of 2",
        doneSetIndex = 1,
        doneExerciseName = if (spanish) "Dominadas" else "Pull-ups",
        loggedSetCount = 8,
    )

    fun summary(spanish: Boolean): SummaryUiState {
        val names = pullRows(spanish).map { it.name }
        val reps = listOf(4 to 0, 13 to 0, 7 to 1, 25 to 0, 12 to 0, 12 to 0)
        return SummaryUiState(
            isLoading = false,
            sessionId = "s",
            routineName = "Pull",
            durationSeconds = 42 * 60 + 10,
            date = today,
            setCount = 14,
            plannedSets = 14,
            bests = listOf(SummaryBest(names[2], 7)),
            rows = names.zip(reps) { name, (r, d) -> SummaryRow(name, r, d) },
            nextRoutine = "Push",
            hasNextStep = true,
        )
    }
}
