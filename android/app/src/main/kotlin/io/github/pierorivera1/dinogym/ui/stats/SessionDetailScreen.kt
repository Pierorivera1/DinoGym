package io.github.pierorivera1.dinogym.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.ConfirmSheet
import io.github.pierorivera1.dinogym.ui.components.Cursor
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.home.shortDate
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat

/** The past session's taps. Each change is stored as it happens (decision 41). */
data class SessionDetailActions(
    val onBack: () -> Unit,
    val onDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onSelectSet: (setId: String) -> Unit,
    val onChangeReps: (delta: Int) -> Unit,
    val onChangeWeight: (steps: Int) -> Unit,
    val onOpenProgress: (exerciseId: String) -> Unit,
) {
    companion object {
        /** No-op actions, for screenshot tests. */
        val None = SessionDetailActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** More chips than this go on their own row under the name, so long names still fit. */
private const val CHIPS_BESIDE_NAME = 3

/**
 * A past session (mock `SessionDetail.dc.html`): top bar with the routine and the delete
 * trash; date, duration and sets; one card per exercise with a chip per set. Tapping a chip
 * opens a stepper under its card; tapping the name opens the exercise's progress. Delete asks
 * first in a [ConfirmSheet]. No bottom bar. Stateless.
 */
@Composable
fun SessionDetailScreen(state: SessionDetailUiState, actions: SessionDetailActions, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val showTrash = !state.isLoading && !state.isMissing
            TopBar(
                title = state.routineName.uppercase(locale),
                onBack = actions.onBack,
                action = if (showTrash) {
                    {
                        DangerIconButton(
                            stringResource(R.string.session_delete_cd),
                            actions.onDelete,
                            icon = R.drawable.ic_px_trash,
                            iconSize = 24.dp,
                        )
                    }
                } else {
                    null
                },
            )
            when {
                state.isLoading -> Unit
                state.isMissing -> EmptyMessage(stringResource(R.string.session_missing))
                else -> {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val sub = stringResource(
                            R.string.session_sub,
                            state.date?.let { shortDate(it, locale) }.orEmpty(),
                            WorkoutFormat.clock(state.durationSeconds),
                            pluralStringResource(R.plurals.workout_sets, state.setCount, state.setCount),
                        )
                        Text(sub, style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 18.sp), color = colors.muted)
                        Text(
                            stringResource(R.string.session_help),
                            style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 17.sp),
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.exercises.forEach { exercise ->
                            ExerciseCard(exercise, state.editing, actions)
                        }
                    }
                }
            }
        }
        if (state.confirmingDelete) {
            ConfirmSheet(
                title = stringResource(R.string.session_delete_title),
                message = stringResource(R.string.session_delete_body),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onCancelDelete,
            )
        }
    }
}

@Composable
private fun ExerciseCard(exercise: SessionExercise, editing: EditingSet?, actions: SessionDetailActions) {
    val colors = DinoTheme.colors
    val chipsBelow = exercise.sets.size > CHIPS_BESIDE_NAME
    val open = editing?.takeIf { it.exerciseId == exercise.exerciseId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clip(SteppedShape)
                .background(colors.surface)
                .steppedBorder(colors.surface2)
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ExerciseName(exercise, actions, Modifier.weight(1f))
                if (!chipsBelow) Chips(exercise, open, actions)
            }
            if (chipsBelow) Chips(exercise, open, actions)
        }
        if (open != null) SetEditor(open, actions)
    }
}

/** Icon, name and notes; the whole block opens the exercise's progress. */
@Composable
private fun ExerciseName(exercise: SessionExercise, actions: SessionDetailActions, modifier: Modifier) {
    val colors = DinoTheme.colors
    val progressDescription = stringResource(R.string.stats_open_progress_cd, exercise.name)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = { actions.onOpenProgress(exercise.exerciseId) })
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = progressDescription
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseTile(exercise.icon, size = 40.dp, iconSize = 32.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                exercise.name,
                style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                color = colors.outline,
            )
            val noteStyle = DinoTheme.type.body.copy(fontSize = 12.sp, lineHeight = 15.sp)
            exercise.template?.let { Text(it, style = noteStyle, color = colors.muted) }
            if (exercise.isNewBest) Text(stringResource(R.string.session_new_best), style = noteStyle, color = colors.muted)
        }
    }
}

@Composable
private fun Chips(exercise: SessionExercise, open: EditingSet?, actions: SessionDetailActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        exercise.sets.forEach { chip ->
            val selected = open?.setId == chip.setId
            // The open chip follows the stepper at once; the database catches up a moment later.
            val reps = if (selected) open.reps else chip.reps
            SetChipBox(chip.setIndex, reps, best = exercise.isNewBest && chip.setIndex == 1, selected) {
                actions.onSelectSet(chip.setId)
            }
        }
    }
}

/** DESIGN.md "Set chip": 48×52, `SET n` over the reps, a `success` ▲ on a set-1 record. */
@Composable
private fun SetChipBox(setIndex: Int, reps: Int, best: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    val description = stringResource(
        R.string.session_set_cd,
        setIndex,
        pluralStringResource(R.plurals.workout_reps, reps, reps.toString()),
    )
    Column(
        Modifier
            .size(width = 48.dp, height = 52.dp)
            .clip(SteppedShape)
            .background(if (selected) colors.selBg else colors.bg)
            .steppedBorder(if (selected) colors.selBorder else colors.outline)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.session_set_chip, setIndex),
            style = DinoTheme.type.body.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
            color = if (selected) colors.selText else colors.muted,
            maxLines = 1,
        )
        Text(
            buildAnnotatedString {
                append(reps.toString())
                if (best) withStyle(SpanStyle(fontSize = 12.sp, color = colors.success)) { append("▲") }
            },
            style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 20.sp),
            color = if (selected) colors.selText else colors.outline,
            maxLines = 1,
        )
    }
}

/**
 * The stepper under a card (selected-state colours, hard shadow, cursor): `SET 2 OF 2` with
 * − reps +, and a KG row when the set was logged with weight. Each tap is saved.
 */
@Composable
private fun SetEditor(editing: EditingSet, actions: SessionDetailActions) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 6.dp)
            .hardShadow(colors.shadow, SteppedShape)
            .clip(SteppedShape)
            .background(colors.selBg)
            .steppedBorder(colors.selBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperRow(
            value = editing.reps.toString(),
            valueWidth = 44.dp,
            valueSize = 34.sp,
            onDecrease = { actions.onChangeReps(-1) },
            onIncrease = { actions.onChangeReps(1) },
            decreaseDescription = stringResource(R.string.cd_one_less_rep),
            increaseDescription = stringResource(R.string.cd_one_more_rep),
        ) {
            Cursor(scale = 3)
            Text(
                stringResource(R.string.workout_set_of, editing.setIndex, editing.setCount),
                style = DinoTheme.type.button.copy(fontSize = 16.sp, lineHeight = 18.sp),
                color = colors.selText,
                maxLines = 1,
            )
        }
        val weight = editing.weightKg
        if (weight != null) {
            StepperRow(
                value = WorkoutFormat.kg(weight, locale),
                valueWidth = 72.dp,
                valueSize = 26.sp,
                onDecrease = { actions.onChangeWeight(-1) },
                onIncrease = { actions.onChangeWeight(1) },
                decreaseDescription = stringResource(R.string.cd_less_kg),
                increaseDescription = stringResource(R.string.cd_more_kg),
            ) {
                Text(stringResource(R.string.label_kg), style = DinoTheme.type.label, color = colors.muted, modifier = Modifier.padding(start = 34.dp))
            }
        }
    }
}

@Composable
private fun StepperRow(
    value: String,
    valueWidth: Dp,
    valueSize: TextUnit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    label: @Composable () -> Unit,
) {
    val colors = DinoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            label()
        }
        RaisedBox(onDecrease, colors.surface, colors.outline, Modifier.size(48.dp).semantics { contentDescription = decreaseDescription }) {
            PixelIcon(R.drawable.ic_px_minus, colors.outline, size = 24.dp)
        }
        Text(
            value,
            style = DinoTheme.type.title.copy(fontSize = valueSize, lineHeight = valueSize),
            color = colors.outline,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(valueWidth),
        )
        RaisedBox(onIncrease, colors.surface, colors.outline, Modifier.size(48.dp).semantics { contentDescription = increaseDescription }) {
            PixelIcon(R.drawable.ic_px_plus, colors.outline, size = 24.dp)
        }
    }
}
