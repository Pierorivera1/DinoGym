package io.github.pierorivera1.dinogym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.components.ConfirmSheet
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.PixelCheckbox
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PixelSwitch
import io.github.pierorivera1.dinogym.ui.components.PixelTextField
import io.github.pierorivera1.dinogym.ui.components.ReorderableColumn
import io.github.pierorivera1.dinogym.ui.components.ReorderableRow
import io.github.pierorivera1.dinogym.ui.components.SectionHeader
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.SwitchRow
import io.github.pierorivera1.dinogym.ui.components.Tag
import io.github.pierorivera1.dinogym.ui.components.TagStyle
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.UndoBar
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.components.drawable
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseEditActions
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseEditScreen
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseEditUiState
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseLibraryScreen
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseLibraryUiState
import io.github.pierorivera1.dinogym.ui.exercises.LibraryFilter
import io.github.pierorivera1.dinogym.ui.exercises.LibraryTile
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Exercises tab (library, editor) and a gallery of the shared form components
 * and every icon, in Night/English and Day/Spanish at the mocks' size (390×844dp), to compare
 * with `ExerciseLibrary.dc.html`, `ExerciseEdit.dc.html` and the other boards by eye.
 * Output: app/build/outputs/roborazzi/exercises_*.png. Nothing is compared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one opens the database, whose native SQLite library is not available on the JVM.
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class ExercisesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun libraryNightEnglish() = capture("exercises_library_en_night", ThemeMode.NIGHT) {
        Library(Samples.library(spanish = false, LibraryFilter.ACTIVE))
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun libraryDaySpanish() = capture("exercises_library_es_day", ThemeMode.DAY) {
        Library(Samples.library(spanish = true, LibraryFilter.ACTIVE))
    }

    @Test
    fun archivedNightEnglish() = capture("exercises_archived_en_night", ThemeMode.NIGHT) {
        Library(Samples.library(spanish = false, LibraryFilter.ARCHIVED))
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun archivedDaySpanish() = capture("exercises_archived_es_day", ThemeMode.DAY) {
        Library(Samples.library(spanish = true, LibraryFilter.ARCHIVED))
    }

    @Test
    fun editPatternNightEnglish() = capture("exercises_edit_pattern_en_night", ThemeMode.NIGHT) {
        ExerciseEditScreen(Samples.lever(spanish = false), ExerciseEditActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun editPatternDaySpanish() = capture("exercises_edit_pattern_es_day", ThemeMode.DAY) {
        ExerciseEditScreen(Samples.lever(spanish = true), ExerciseEditActions.None)
    }

    @Test
    fun editRepsArchivedNightEnglish() = capture("exercises_edit_reps_en_night", ThemeMode.NIGHT) {
        ExerciseEditScreen(Samples.dips(spanish = false), ExerciseEditActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun editRepsArchivedDaySpanish() = capture("exercises_edit_reps_es_day", ThemeMode.DAY) {
        ExerciseEditScreen(Samples.dips(spanish = true), ExerciseEditActions.None)
    }

    /** A pattern without {n} and a blank name: both show their error. */
    @Test
    fun editErrorsNightEnglish() = capture("exercises_edit_errors_en_night", ThemeMode.NIGHT) {
        ExerciseEditScreen(Samples.lever(spanish = false).copy(name = "", pattern = "3s + pulls"), ExerciseEditActions.None)
    }

    @Test
    @Config(qualifiers = GALLERY)
    fun galleryNightEnglish() = capture("exercises_gallery_en_night", ThemeMode.NIGHT) { Gallery() }

    @Test
    @Config(qualifiers = GALLERY_SPANISH)
    fun galleryDaySpanish() = capture("exercises_gallery_es_day", ThemeMode.DAY) { Gallery() }

    @Test
    fun confirmSheetNightEnglish() = capture("exercises_confirm_en_night", ThemeMode.NIGHT) { ConfirmOverEditor() }

    @Test
    @Config(qualifiers = SPANISH)
    fun confirmSheetDaySpanish() = capture("exercises_confirm_es_day", ThemeMode.DAY) { ConfirmOverEditor() }

    @Composable
    private fun Library(state: ExerciseLibraryUiState) {
        AppShell(tab = BottomTab.EXERCISES, onTabClick = {}, onStartClick = {}) {
            ExerciseLibraryScreen(state, onNew = {}, onSelectFilter = {}, onOpenExercise = {})
        }
    }

    @Composable
    private fun ConfirmOverEditor() {
        Box(Modifier.fillMaxSize()) {
            ExerciseEditScreen(Samples.dips(spanish = false), ExerciseEditActions.None)
            ConfirmSheet(
                title = "Delete program?",
                message = "My program and its cycle go away. Routines and history stay.",
                confirmLabel = "DELETE",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent { DinoTheme(mode) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SPANISH = "es-w390dp-h844dp-420dpi"
        const val GALLERY = "en-w390dp-h1300dp-420dpi"
        const val GALLERY_SPANISH = "es-w390dp-h1300dp-420dpi"
    }
}

/** The shared form components and every icon, on one page. */
@Composable
private fun Gallery() {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title = "EDIT PROGRAM", onBack = {}, action = {
            DangerIconButton("Delete", {}, icon = R.drawable.ic_px_trash, iconSize = 24.dp)
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag("ACTIVE", style = TagStyle.Outlined, color = colors.outline)
            Tag("NEXT")
            Tag("IN ROUTINE", style = TagStyle.Outlined)
            Text("Changes save as you go.", style = DinoTheme.type.body.copy(fontSize = 13.sp), color = colors.muted)
        }
        SectionHeader("YOUR PROGRAMS", onNew = {}, newContentDescription = "New program")
        PixelTextField("My program", {}, label = "NAME")
        PixelTextField("pull", {}, contentDescription = "Search", leadingIcon = R.drawable.ic_px_search, trailing = {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { PixelIcon(R.drawable.ic_px_close, colors.muted, size = 24.dp) }
        })
        SegmentedControl(listOf("GYM", "CALISTHENICS"), selectedIndex = 1, onSelect = {})
        SegmentedControl(listOf("SYSTEM", "NIGHT", "DAY", "OTHER"), selectedIndex = 0, onSelect = {}, asTabs = true)
        SwitchRow("Log weight", checked = true, onCheckedChange = {}, help = "On: the weight field shows in every program.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelSwitch(checked = false)
            PixelSwitch(checked = true)
            PixelCheckbox(checked = false)
            PixelCheckbox(checked = true)
        }
        val steps = listOf("PULL" to "6 exercises", "PUSH" to "5 exercises", null to "Rest day")
        ReorderableColumn(
            items = steps,
            key = { it.second },
            onMove = { _, _ -> },
            modifier = Modifier.fillMaxWidth().clip(SteppedShape).steppedBorder(colors.outline),
        ) { index, (name, meta) ->
            ReorderableRow(
                dragDescription = "Move step ${index + 1}",
                onRemove = {},
                modifier = Modifier.dottedBottomLine(colors.surface2),
            ) {
                Text("${index + 1}", style = DinoTheme.type.button.copy(fontSize = 15.sp), color = colors.muted, modifier = Modifier.width(20.dp))
                if (name != null) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = DinoTheme.type.button, color = colors.outline)
                            if (index == 0) Tag("NEXT")
                        }
                        Text(meta, style = DinoTheme.type.body.copy(fontSize = 13.sp), color = colors.muted)
                    }
                    PixelIcon(R.drawable.ic_px_chevron, colors.muted, size = 20.dp)
                } else {
                    PixelIcon(R.drawable.ic_px_zzz, colors.muted, size = 24.dp)
                    Text(meta, style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        UndoBar("Removed Legs from the cycle", onUndo = {}, onTimeout = {})

        val icons = listOf(
            R.drawable.ic_px_home, R.drawable.ic_px_programs, R.drawable.ic_px_start, R.drawable.ic_px_stats,
            R.drawable.ic_px_exercises, R.drawable.ic_px_check, R.drawable.ic_px_timer, R.drawable.ic_px_plus,
            R.drawable.ic_px_minus, R.drawable.ic_px_close, R.drawable.ic_px_edit, R.drawable.ic_px_skip,
            R.drawable.ic_px_gear, R.drawable.ic_px_back, R.drawable.ic_px_chevron, R.drawable.ic_px_drag,
            R.drawable.ic_px_trash, R.drawable.ic_px_search, R.drawable.ic_px_archive, R.drawable.ic_px_zzz,
        ) + ExerciseIcon.entries.map { it.drawable() }
        icons.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { icon ->
                    Box(Modifier.size(42.dp).clip(SteppedShape).background(colors.surface2), contentAlignment = Alignment.Center) {
                        PixelIcon(icon, colors.outline)
                    }
                }
            }
        }
    }
}

/** The data of the mocks, in English or Spanish (exercise names are user data). */
private object Samples {
    private val english = listOf(
        ExerciseIcon.CURL to "Bicep curl", ExerciseIcon.SQUAT to "Bulgarian split squat", ExerciseIcon.DIP to "Dips",
        ExerciseIcon.CURL to "Forearm curl", ExerciseIcon.LEVER to "Front lever + pulls", ExerciseIcon.PUSH_UP to "Hollow push-ups",
        ExerciseIcon.JUMP_ROPE to "Jump rope", ExerciseIcon.RAISE to "Lateral raises", ExerciseIcon.PULL_UP to "Neutral-grip pull-ups",
        ExerciseIcon.PULL_UP to "Pull-ups", ExerciseIcon.PUSH_UP to "Push-ups", ExerciseIcon.PUSH_UP to "Push-ups (triceps)",
        ExerciseIcon.CURL to "Reverse curl", ExerciseIcon.ROW to "Rows", ExerciseIcon.RAISE to "Single-arm lateral raises",
        ExerciseIcon.SQUAT to "Squats",
    )
    private val spanish = listOf(
        ExerciseIcon.CURL to "Curl de antebrazo", ExerciseIcon.CURL to "Curl de bíceps", ExerciseIcon.CURL to "Curl inverso",
        ExerciseIcon.PULL_UP to "Dominadas", ExerciseIcon.PULL_UP to "Dominadas agarre neutro", ExerciseIcon.RAISE to "Elevaciones laterales",
        ExerciseIcon.RAISE to "Elevaciones laterales a una mano", ExerciseIcon.PUSH_UP to "Flexiones", ExerciseIcon.PUSH_UP to "Flexiones (tríceps)",
        ExerciseIcon.PUSH_UP to "Flexiones hollow", ExerciseIcon.DIP to "Fondos", ExerciseIcon.LEVER to "Front lever + dominadas",
        ExerciseIcon.ROW to "Remo", ExerciseIcon.JUMP_ROPE to "Saltar soga", ExerciseIcon.SQUAT to "Sentadilla búlgara",
        ExerciseIcon.SQUAT to "Sentadillas",
    )

    fun library(spanish: Boolean, filter: LibraryFilter): ExerciseLibraryUiState {
        val active = (if (spanish) this.spanish else english).mapIndexed { i, (icon, name) -> LibraryTile("a$i", name, icon) }
        val archived = listOf(LibraryTile("x", if (spanish) "Remo invertido" else "Inverted rows", ExerciseIcon.ROW))
        return ExerciseLibraryUiState(
            isLoading = false,
            filter = filter,
            activeCount = active.size,
            archivedCount = archived.size,
            tiles = if (filter == LibraryFilter.ACTIVE) active else archived,
        )
    }

    fun lever(spanish: Boolean) = ExerciseEditUiState(
        isLoading = false,
        name = if (spanish) "Front lever + dominadas" else "Front lever + pulls",
        icon = ExerciseIcon.LEVER,
        kind = ExerciseKind.TEMPLATE,
        pattern = if (spanish) "3s + {n} dominadas + 3s" else "3s + {n} pulls + 3s",
        weightEnabled = false,
        archived = false,
        usedIn = listOf("Pull", "Upper"),
    )

    fun dips(spanish: Boolean) = ExerciseEditUiState(
        isLoading = false,
        name = if (spanish) "Fondos" else "Dips",
        icon = ExerciseIcon.DIP,
        kind = ExerciseKind.REPS,
        weightEnabled = true,
        archived = true,
        usedIn = listOf("Push", "Upper", "Full body"),
    )
}
