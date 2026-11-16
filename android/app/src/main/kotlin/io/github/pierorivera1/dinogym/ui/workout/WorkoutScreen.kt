package io.github.pierorivera1.dinogym.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.ui.components.ActiveSetCard
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.SetRow
import io.github.pierorivera1.dinogym.ui.components.StatBar
import io.github.pierorivera1.dinogym.ui.components.Stepper
import io.github.pierorivera1.dinogym.ui.components.dottedBorder
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * Every tap the active exercise screen reports, grouped so the screen's signature stays short.
 * The entry fills it from [WorkoutViewModel]; screenshot tests pass [WorkoutActions.None].
 */
data class WorkoutActions(
    val onEnd: () -> Unit,
    val onChangeReps: (Int) -> Unit,
    val onChangeWeight: (Double) -> Unit,
    val onLogSet: () -> Unit,
    val onOpenRestPicker: () -> Unit,
    val onOpenNote: () -> Unit,
    val onSkip: () -> Unit,
    val onOpenSet: (String) -> Unit,
    val onCloseSheet: () -> Unit,
    val onSaveRest: (Int) -> Unit,
    val onSaveNote: (String) -> Unit,
    val onSaveSet: (String, Int, Double?) -> Unit,
    val onDeleteSet: (String) -> Unit,
    val onSaveAndFinish: () -> Unit,
    val onDiscard: () -> Unit,
) {
    companion object {
        /** Does nothing: for previews and screenshot tests. */
        val None = WorkoutActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, {})
    }
}

/**
 * The active exercise (mock `Workout.dc.html`): top bar with ✕, routine and elapsed time;
 * the `EXERCISE n/N` bar; the exercise; done sets; the active set card; LOG SET; rest time,
 * NOTE and SKIP; the next exercise at the bottom. Sheets (rest picker, note, set edit, end)
 * draw over it. Stateless: it draws [state] and reports taps through [actions].
 */
@Composable
fun WorkoutScreen(state: WorkoutUiState, actions: WorkoutActions, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WorkoutTopBar(state.routineName, state.elapsedSeconds, actions.onEnd)
            val exercise = state.exercise
            if (state.isLoading || exercise == null) return@Column

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatBar(
                    label = stringResource(R.string.label_exercise),
                    total = state.exerciseCount,
                    filled = state.position,
                    value = "${state.position + 1}/${state.exerciseCount}",
                    markCurrent = true,
                )
                ExerciseHeader(exercise)
                exercise.doneSets.forEach { set -> DoneSet(set, actions.onOpenSet) }
                if (exercise.kind == ExerciseKind.SHOW_ONLY) {
                    ShowOnlyPart(actions)
                } else {
                    ActiveSet(exercise, actions)
                }
            }
            NextBox(state.next)
        }
        Sheet(state, actions)
    }
}

/** The top bar shared by the active exercise and the rest timer: ✕, routine name, elapsed time. */
@Composable
internal fun WorkoutTopBar(routineName: String, elapsedSeconds: Long, onEnd: () -> Unit) {
    val colors = DinoTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        DangerIconButton(stringResource(R.string.cd_end_workout), onEnd)
        Text(
            routineName.uppercase(),
            style = DinoTheme.type.title.copy(fontSize = 24.sp, lineHeight = 28.sp),
            color = colors.outline,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(
            WorkoutFormat.clock(elapsedSeconds),
            style = DinoTheme.type.button,
            color = colors.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun ExerciseHeader(exercise: ActiveExerciseUi) {
    val colors = DinoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ExerciseTile(exercise.icon)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(exercise.name, style = DinoTheme.type.heading, color = colors.outline, modifier = Modifier.semantics { heading() })
            Text(targetLine(exercise), style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 19.sp), color = colors.muted)
        }
    }
}

/** "2 sets × 6 reps · last time 6 · 6". */
@Composable
private fun targetLine(exercise: ActiveExerciseUi): String {
    val sets = pluralStringResource(R.plurals.workout_sets, exercise.sets, exercise.sets)
    val reps = pluralStringResource(R.plurals.workout_reps, exercise.repMax, WorkoutFormat.reps(exercise.repMin, exercise.repMax))
    val target = stringResource(R.string.workout_target, sets, reps)
    if (exercise.kind == ExerciseKind.SHOW_ONLY || exercise.lastTime.isEmpty()) return target
    return "$target · " + stringResource(R.string.workout_last_time, exercise.lastTime.joinToString(" · "))
}

@Composable
private fun DoneSet(set: DoneSetUi, onOpenSet: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val unit = pluralStringResource(R.plurals.workout_reps_unit, set.reps)
    SetRow(
        label = stringResource(R.string.workout_set_label, set.setIndex),
        value = set.reps.toString(),
        unit = if (set.weightKg != null) "$unit · ${WorkoutFormat.kg(set.weightKg, locale)} kg" else unit,
        onClick = { onOpenSet(set.id) },
        onClickLabel = stringResource(R.string.workout_edit_set),
        newBestTag = if (set.isNewBest) stringResource(R.string.label_new_best) else null,
    )
}

@Composable
private fun ActiveSet(exercise: ActiveExerciseUi, actions: WorkoutActions) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val footer = when {
        exercise.note.isNotBlank() -> stringResource(R.string.workout_note_preview, exercise.note)
        exercise.preFilledFromHistory -> stringResource(R.string.workout_prefilled)
        else -> stringResource(R.string.workout_prefilled_target)
    }
    ActiveSetCard(
        title = stringResource(R.string.workout_set_of, exercise.nextSetIndex, exercise.sets),
        footer = footer,
    ) {
        if (exercise.template != null) {
            Text(
                WorkoutFormat.template(exercise.template, exercise.reps),
                style = DinoTheme.type.body.copy(fontSize = 15.sp),
                color = colors.selText,
            )
        }
        Stepper(
            label = stringResource(R.string.label_reps),
            value = exercise.reps,
            onDecrease = { actions.onChangeReps(-1) },
            onIncrease = { actions.onChangeReps(1) },
            decreaseDescription = stringResource(R.string.cd_one_less_rep),
            increaseDescription = stringResource(R.string.cd_one_more_rep),
            modifier = Modifier.fillMaxWidth(),
        )
        if (exercise.weightKg != null) {
            Stepper(
                label = stringResource(R.string.label_kg),
                value = exercise.weightKg.toInt(),
                valueText = WorkoutFormat.kg(exercise.weightKg, locale),
                onDecrease = { actions.onChangeWeight(-WEIGHT_STEP_KG) },
                onIncrease = { actions.onChangeWeight(WEIGHT_STEP_KG) },
                decreaseDescription = stringResource(R.string.cd_less_kg),
                increaseDescription = stringResource(R.string.cd_more_kg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    PrimaryButton(
        stringResource(R.string.action_log_set),
        actions.onLogSet,
        Modifier.fillMaxWidth().height(64.dp),
        icon = R.drawable.ic_px_check,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val restDescription = stringResource(R.string.cd_rest_time, spokenDuration(exercise.restSeconds))
        SecondaryButton(
            WorkoutFormat.clock(exercise.restSeconds.toLong()),
            actions.onOpenRestPicker,
            Modifier.weight(1f).height(52.dp).semantics { contentDescription = restDescription },
            icon = R.drawable.ic_px_timer,
        )
        SecondaryButton(stringResource(R.string.action_note), actions.onOpenNote, Modifier.weight(1f).height(52.dp))
        SecondaryButton(stringResource(R.string.action_skip), actions.onSkip, Modifier.weight(1f).height(52.dp))
    }
}

/** A show-only exercise (jump rope): nothing to log, just DONE or SKIP. */
@Composable
private fun ShowOnlyPart(actions: WorkoutActions) {
    Text(
        stringResource(R.string.workout_show_only),
        style = DinoTheme.type.body,
        color = DinoTheme.colors.muted,
    )
    PrimaryButton(
        stringResource(R.string.action_done),
        actions.onLogSet,
        Modifier.fillMaxWidth().height(64.dp),
        icon = R.drawable.ic_px_check,
    )
}

/** The dotted box at the bottom: `NEXT Reverse curl   2 × 25`, or "Finish workout" after the last. */
@Composable
private fun NextBox(next: NextExerciseUi?) {
    val colors = DinoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .dottedBorder(colors.muted)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(DinoTheme.type.label.toSpanStyle().copy(color = colors.muted)) {
                    append(stringResource(R.string.label_next))
                }
                append(" ")
                append(next?.name ?: stringResource(R.string.workout_next_finish))
            },
            style = DinoTheme.type.body.copy(fontSize = 14.sp),
            color = colors.outline,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (next != null && next.kind != ExerciseKind.SHOW_ONLY) {
            Text(
                WorkoutFormat.setsTimesReps(next.sets, next.repMin, next.repMax),
                style = DinoTheme.type.button.copy(fontSize = 15.sp),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun Sheet(state: WorkoutUiState, actions: WorkoutActions) {
    when (val sheet = state.sheet) {
        WorkoutSheet.None -> Unit
        is WorkoutSheet.RestPicker -> RestPickerSheet(
            exerciseName = state.exercise?.name.orEmpty(),
            routineName = state.routineName,
            initialSeconds = sheet.seconds,
            onSave = actions.onSaveRest,
            onClose = actions.onCloseSheet,
        )
        is WorkoutSheet.Note -> NoteSheet(sheet.text, actions.onSaveNote, actions.onCloseSheet)
        is WorkoutSheet.EditSet -> EditSetSheet(
            sheet = sheet,
            onSave = { reps, kg -> actions.onSaveSet(sheet.setId, reps, kg) },
            onDelete = { actions.onDeleteSet(sheet.setId) },
            onClose = actions.onCloseSheet,
        )
        WorkoutSheet.End -> EndWorkoutSheet(state.loggedSetCount, actions.onSaveAndFinish, actions.onDiscard, actions.onCloseSheet)
    }
}

/** "2 minutes 30 seconds", for screen readers. */
@Composable
internal fun spokenDuration(totalSeconds: Int): String {
    val (minutes, seconds) = WorkoutFormat.minutesAndSeconds(totalSeconds)
    val minuteText = pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
    val secondText = pluralStringResource(R.plurals.duration_seconds, seconds, seconds)
    return when {
        minutes == 0 -> secondText
        seconds == 0 -> minuteText
        else -> "$minuteText $secondText"
    }
}
