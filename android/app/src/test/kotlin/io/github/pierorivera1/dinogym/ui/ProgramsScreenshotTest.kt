package io.github.pierorivera1.dinogym.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.home.CycleCell
import io.github.pierorivera1.dinogym.ui.programs.CycleStepUi
import io.github.pierorivera1.dinogym.ui.programs.PickerRow
import io.github.pierorivera1.dinogym.ui.programs.PickerUiState
import io.github.pierorivera1.dinogym.ui.programs.ProgramCardUi
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditActions
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditScreen
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditUiState
import io.github.pierorivera1.dinogym.ui.programs.ProgramListScreen
import io.github.pierorivera1.dinogym.ui.programs.ProgramListUiState
import io.github.pierorivera1.dinogym.ui.programs.ProgramSheet
import io.github.pierorivera1.dinogym.ui.programs.RemovedExercise
import io.github.pierorivera1.dinogym.ui.programs.RemovedStep
import io.github.pierorivera1.dinogym.ui.programs.RoutineChoice
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditActions
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditScreen
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditUiState
import io.github.pierorivera1.dinogym.ui.programs.RoutineItemUi
import io.github.pierorivera1.dinogym.ui.programs.RoutineRowUi
import io.github.pierorivera1.dinogym.ui.programs.RoutineSheet
import io.github.pierorivera1.dinogym.ui.programs.Target
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Programs screens (list, program editor, routine editor, exercise picker and
 * their sheets) in Night/English and Day/Spanish at the mocks' size (390×844dp), to compare
 * with `ProgramList.dc.html`, `ProgramEdit.dc.html`, `RoutineEdit.dc.html` and
 * `ExercisePicker.dc.html` by eye. Output: app/build/outputs/roborazzi/programs_*.png.
 * Nothing is compared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one opens the database, whose native SQLite library is not available on the JVM.
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class ProgramsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listNightEnglish() = capture("programs_list_en_night", ThemeMode.NIGHT) { List(ProgramSamples.list(spanish = false)) }

    @Test
    @Config(qualifiers = SPANISH)
    fun listDaySpanish() = capture("programs_list_es_day", ThemeMode.DAY) { List(ProgramSamples.list(spanish = true)) }

    @Test
    fun programNightEnglish() = capture("programs_edit_en_night", ThemeMode.NIGHT) {
        ProgramEditScreen(ProgramSamples.program(spanish = false, active = true), ProgramEditActions.None)
    }

    /** An inactive program shows USE THIS PROGRAM; a step was just removed, so UNDO shows. */
    @Test
    @Config(qualifiers = SPANISH)
    fun programInactiveDaySpanish() = capture("programs_edit_inactive_es_day", ThemeMode.DAY) {
        val state = ProgramSamples.program(spanish = true, active = false)
        ProgramEditScreen(
            state.copy(steps = state.steps.take(4), undo = RemovedStep("s4", 4, false, "Upper")),
            ProgramEditActions.None,
        )
    }

    @Test
    fun addRoutineSheetNightEnglish() = capture("programs_add_routine_en_night", ThemeMode.NIGHT) {
        ProgramEditScreen(ProgramSamples.program(spanish = false, active = true).copy(sheet = ProgramSheet.AddRoutine), ProgramEditActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun deleteProgramDaySpanish() = capture("programs_delete_es_day", ThemeMode.DAY) {
        ProgramEditScreen(ProgramSamples.program(spanish = true, active = true).copy(sheet = ProgramSheet.ConfirmDelete), ProgramEditActions.None)
    }

    @Test
    fun routineNightEnglish() = capture("programs_routine_en_night", ThemeMode.NIGHT) {
        RoutineEditScreen(ProgramSamples.routine(spanish = false), RoutineEditActions.None)
    }

    /** RANGE on the open card, and the UNDO bar after a removal. */
    @Test
    @Config(qualifiers = SPANISH)
    fun routineRangeDaySpanish() = capture("programs_routine_range_es_day", ThemeMode.DAY) {
        val state = ProgramSamples.routine(spanish = true)
        val items = state.items.map { if (it.id == state.expandedId) it.copy(target = Target(2, 6, 10, 150)) else it }
        RoutineEditScreen(
            state.copy(items = items, undo = RemovedExercise("x", 5, "Curl de bíceps")),
            RoutineEditActions.None,
        )
    }

    @Test
    fun restPickerNightEnglish() = capture("programs_rest_en_night", ThemeMode.NIGHT) {
        val state = ProgramSamples.routine(spanish = false)
        RoutineEditScreen(state.copy(sheet = RoutineSheet.Rest("i2", "Pull-ups", 150)), RoutineEditActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun deleteRoutineDaySpanish() = capture("programs_delete_routine_es_day", ThemeMode.DAY) {
        val state = ProgramSamples.routine(spanish = true)
        RoutineEditScreen(state.copy(sheet = RoutineSheet.ConfirmDelete(listOf("Mi programa", "Semana corta"))), RoutineEditActions.None)
    }

    @Test
    fun pickerNightEnglish() = capture("programs_picker_en_night", ThemeMode.NIGHT) {
        RoutineEditScreen(ProgramSamples.routine(spanish = false).copy(picker = ProgramSamples.picker(spanish = false)), RoutineEditActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun pickerDaySpanish() = capture("programs_picker_es_day", ThemeMode.DAY) {
        RoutineEditScreen(ProgramSamples.routine(spanish = true).copy(picker = ProgramSamples.picker(spanish = true)), RoutineEditActions.None)
    }

    @Composable
    private fun List(state: ProgramListUiState) {
        AppShell(tab = BottomTab.PROGRAMS, onTabClick = {}, onStartClick = {}) {
            ProgramListScreen(state, onNewProgram = {}, onNewRoutine = {}, onOpenProgram = {}, onOpenRoutine = {})
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

/** The data of the mocks, in English or Spanish (program, routine and exercise names are user data). */
private object ProgramSamples {
    private val ownerCycle = listOf("Pull", "Push", "Legs", null, "Upper", "Lower")

    fun list(spanish: Boolean) = ProgramListUiState(
        isLoading = false,
        programs = listOf(
            ProgramCardUi(
                "mine", if (spanish) "Mi programa" else "My program", ProgramType.CALISTHENICS, isActive = true,
                stepCount = 6, nextRoutine = "Pull", nextIsRest = false,
                cycle = ownerCycle.mapIndexed { i, r -> CycleCell(r, isCurrent = i == 0) },
            ),
            ProgramCardUi(
                "short", if (spanish) "Semana corta" else "Short week", ProgramType.CALISTHENICS, isActive = false,
                stepCount = 5, nextRoutine = "Upper", nextIsRest = false,
                cycle = listOf("Upper", "Lower", null, "Upper", "Lower").map { CycleCell(it, isCurrent = false) },
            ),
        ),
        routines = listOf(
            RoutineRowUi("pull", "Pull", 6, 14), RoutineRowUi("push", "Push", 5, 11), RoutineRowUi("legs", "Legs", 3, 5),
            RoutineRowUi("upper", "Upper", 7, 15), RoutineRowUi("lower", "Lower", 3, 5),
        ),
    )

    fun program(spanish: Boolean, active: Boolean) = ProgramEditUiState(
        isLoading = false,
        name = if (spanish) "Mi programa" else "My program",
        type = ProgramType.CALISTHENICS,
        isActive = active,
        steps = ownerCycle.mapIndexed { i, r ->
            CycleStepUi("s$i", r?.lowercase(), r, exerciseCount = listOf(6, 5, 3, 0, 7, 3)[i], isNext = i == 0)
        },
        routines = listOf(
            RoutineChoice("legs", "Legs", 3), RoutineChoice("lower", "Lower", 3), RoutineChoice("pull", "Pull", 6),
            RoutineChoice("push", "Push", 5), RoutineChoice("upper", "Upper", 7),
        ),
    )

    fun routine(spanish: Boolean): RoutineEditUiState {
        val names = if (spanish) {
            listOf("Front lever + dominadas", "Curl de antebrazo", "Dominadas", "Curl inverso", "Remo", "Curl de bíceps")
        } else {
            listOf("Front lever + pulls", "Forearm curl", "Pull-ups", "Reverse curl", "Rows", "Bicep curl")
        }
        val icons = listOf(ExerciseIcon.LEVER, ExerciseIcon.CURL, ExerciseIcon.PULL_UP, ExerciseIcon.CURL, ExerciseIcon.ROW, ExerciseIcon.CURL)
        val targets = listOf(Target(2, 4, 4, 150), Target(3, 13, 13, 90), Target(2, 6, 6, 150), Target(2, 25, 25, 90), Target(3, 12, 12, 150), Target(2, 12, 12, 90))
        return RoutineEditUiState(
            isLoading = false,
            name = "Pull",
            savedName = "Pull",
            items = names.indices.map { i ->
                RoutineItemUi("i$i", "e$i", names[i], icons[i], if (i == 0) ExerciseKind.TEMPLATE else ExerciseKind.REPS, targets[i])
            },
            expandedId = "i2",
        )
    }

    fun picker(spanish: Boolean) = if (spanish) {
        PickerUiState(
            query = "domin",
            selectedCount = 1,
            rows = listOf(
                PickerRow("a", "Dominadas", ExerciseIcon.PULL_UP, ExerciseKind.REPS, null, inRoutine = true, checked = false),
                PickerRow("b", "Dominadas agarre neutro", ExerciseIcon.PULL_UP, ExerciseKind.REPS, null, inRoutine = false, checked = true),
                PickerRow("c", "Front lever + dominadas", ExerciseIcon.LEVER, ExerciseKind.TEMPLATE, "3s + {n} dominadas + 3s", inRoutine = true, checked = false),
            ),
        )
    } else {
        PickerUiState(
            query = "pull",
            selectedCount = 1,
            rows = listOf(
                PickerRow("c", "Front lever + pulls", ExerciseIcon.LEVER, ExerciseKind.TEMPLATE, "3s + {n} pulls + 3s", inRoutine = true, checked = false),
                PickerRow("b", "Neutral-grip pull-ups", ExerciseIcon.PULL_UP, ExerciseKind.REPS, null, inRoutine = false, checked = true),
                PickerRow("a", "Pull-ups", ExerciseIcon.PULL_UP, ExerciseKind.REPS, null, inRoutine = true, checked = false),
            ),
        )
    }
}
