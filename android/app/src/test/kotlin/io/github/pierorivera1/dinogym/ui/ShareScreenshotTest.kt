package io.github.pierorivera1.dinogym.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.data.share.SharedContent
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditActions
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditScreen
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditActions
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditScreen
import io.github.pierorivera1.dinogym.ui.share.ImportActions
import io.github.pierorivera1.dinogym.ui.share.ImportScreen
import io.github.pierorivera1.dinogym.ui.share.ImportUiState
import io.github.pierorivera1.dinogym.ui.share.ShareSheet
import io.github.pierorivera1.dinogym.ui.share.ShareTarget
import io.github.pierorivera1.dinogym.ui.share.ShareUiState
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the share sheet and the import screen (task 23) in Night/English and Day/Spanish
 * at the mock's size, to compare with `ShareFlow.dc.html` and `ImportShare.dc.html` by eye.
 * Output: app/build/outputs/roborazzi/share_*.png. Nothing is compared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class ShareScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** The whole routine editor, to show SHARE ROUTINE under the list. */
    @Test
    @Config(qualifiers = "en-w390dp-h1300dp-420dpi")
    fun routineEditorButtonNightEnglish() = capture("share_routine_button_en_night", ThemeMode.NIGHT) {
        RoutineEditScreen(ProgramSamples.routine(spanish = false).copy(expandedId = null), RoutineEditActions.None)
    }

    @Test
    fun shareDoneNightEnglish() = capture("share_done_en_night", ThemeMode.NIGHT) {
        Box {
            RoutineEditScreen(ProgramSamples.routine(spanish = false).copy(expandedId = null), RoutineEditActions.None)
            Sheet(ShareUiState(ShareTarget.Routine("r"), ShareUiState.Step.DONE, url = "https://d1x2y3z4abcd.cloudfront.net/r/q3V9xY_-AbCdEfGhIjKlMn"))
        }
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun shareSignedOutDaySpanish() = capture("share_signed_out_es_day", ThemeMode.DAY) {
        Box {
            ProgramEditScreen(ProgramSamples.program(spanish = true, active = true), ProgramEditActions.None)
            Sheet(ShareUiState(ShareTarget.Program("p"), ShareUiState.Step.SIGNED_OUT))
        }
    }

    @Test
    fun shareOfflineNightEnglish() = capture("share_offline_en_night", ThemeMode.NIGHT) {
        Box {
            ProgramEditScreen(ProgramSamples.program(spanish = false, active = true), ProgramEditActions.None)
            Sheet(ShareUiState(ShareTarget.Program("p"), ShareUiState.Step.OFFLINE))
        }
    }

    @Test
    fun importProgramNightEnglish() = capture("share_import_program_en_night", ThemeMode.NIGHT) {
        ImportScreen(
            ImportUiState(ImportUiState.Step.PREVIEW, content = SharedContent.Program(upperLower), newExercises = setOf(bench)),
            ImportActions.None,
        )
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun importRoutineDaySpanish() = capture("share_import_routine_es_day", ThemeMode.DAY) {
        ImportScreen(
            ImportUiState(ImportUiState.Step.PREVIEW, content = SharedContent.Routine(pull), newExercises = setOf(archer)),
            ImportActions.None,
        )
    }

    @Test
    fun importOfflineNightEnglish() = capture("share_import_offline_en_night", ThemeMode.NIGHT) {
        ImportScreen(ImportUiState(ImportUiState.Step.OFFLINE), ImportActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun importPasteInvalidDaySpanish() = capture("share_import_paste_es_day", ThemeMode.DAY) {
        ImportScreen(ImportUiState(ImportUiState.Step.PASTE, pasted = "hola", pasteInvalid = true), ImportActions.None)
    }

    @Composable
    private fun Sheet(state: ShareUiState) = ShareSheet(state, onClose = {}, onRetry = {}, onSend = {}, onOpenSettings = {})

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent { DinoTheme(mode) { content() } }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SPANISH = "es-w390dp-h844dp-420dpi"

        val bench = ExerciseTemplate("Bench press", ExerciseIcon.PUSH_UP, ExerciseKind.REPS, weightEnabled = true)
        val pullUp = ExerciseTemplate("Pull-ups", ExerciseIcon.PULL_UP, ExerciseKind.REPS)
        val squat = ExerciseTemplate("Squat", ExerciseIcon.SQUAT, ExerciseKind.REPS, weightEnabled = true)
        val archer = ExerciseTemplate("Remo arquero", ExerciseIcon.ROW, ExerciseKind.REPS)
        val upperLower = ProgramTemplate(
            "Upper / Lower",
            ProgramType.GYM,
            listOf(
                RoutineTemplate("Upper", listOf(RoutineItemTemplate(bench, 3, 6, 8, 150), RoutineItemTemplate(pullUp, 3, 5, 8, 150))),
                RoutineTemplate("Lower", listOf(RoutineItemTemplate(squat, 3, 5, 8, 180))),
            ),
            listOf(0, 1, null, 0, 1, null),
        )
        val pull = RoutineTemplate(
            "PULL",
            listOf(
                RoutineItemTemplate(ExerciseTemplate("Muscle-up", ExerciseIcon.LEVER, ExerciseKind.TEMPLATE, "3s + {n} pulls + 3s"), 2, 3, 5, 150),
                RoutineItemTemplate(ExerciseTemplate("Dominadas", ExerciseIcon.PULL_UP, ExerciseKind.REPS), 3, 6, 10, 150),
                RoutineItemTemplate(archer, 3, 10, 12, 90),
            ),
        )
    }
}
