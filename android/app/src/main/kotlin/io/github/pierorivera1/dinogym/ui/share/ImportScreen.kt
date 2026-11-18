package io.github.pierorivera1.dinogym.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.share.SharedContent
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.PixelTextField
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.Tag
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.home.CycleCell
import io.github.pierorivera1.dinogym.ui.programs.CompactCycleStrip
import io.github.pierorivera1.dinogym.ui.programs.GroupLabel
import io.github.pierorivera1.dinogym.ui.programs.HelpText
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat

/** What the import screen can do. */
data class ImportActions(
    val onBack: () -> Unit,
    val onPastedChange: (String) -> Unit,
    val onOpenPasted: () -> Unit,
    val onRetry: () -> Unit,
    val onAdd: () -> Unit,
) {
    companion object {
        val None = ImportActions({}, {}, {}, {}, {})
    }
}

/**
 * The import screen (mock `ImportShare.dc.html`, decision 102): a pushed screen with the top
 * bar. With no link yet, a field to paste one. Then loading, the reasons a link cannot be
 * read (offline, unknown, from a newer app, an error), or the preview: what is shared, its
 * cycle, every routine with its exercises (NEW on those the library does not have), a text
 * box saying what ADD will do, and ADD ROUTINE / ADD PROGRAM fixed at the bottom.
 * Stateless.
 */
@Composable
fun ImportScreen(state: ImportUiState, actions: ImportActions, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TopBar(title = stringResource(R.string.import_title), onBack = actions.onBack)
                when (state.step) {
                    ImportUiState.Step.PASTE -> PasteLink(state, actions)
                    ImportUiState.Step.LOADING -> Message(stringResource(R.string.import_loading), error = false)
                    ImportUiState.Step.OFFLINE -> Problem(stringResource(R.string.import_offline), actions.onRetry)
                    ImportUiState.Step.NOT_FOUND -> Message(stringResource(R.string.import_not_found), error = true)
                    ImportUiState.Step.UNREADABLE -> Message(stringResource(R.string.import_unreadable), error = true)
                    ImportUiState.Step.FAILED -> Problem(stringResource(R.string.import_failed), actions.onRetry)
                    ImportUiState.Step.PREVIEW, ImportUiState.Step.ADDING -> state.content?.let { Preview(it, state) }
                }
            }
            val content = state.content
            if (content != null && (state.step == ImportUiState.Step.PREVIEW || state.step == ImportUiState.Step.ADDING)) {
                val label = when {
                    state.step == ImportUiState.Step.ADDING -> stringResource(R.string.import_adding)
                    content is SharedContent.Program -> stringResource(R.string.import_add_program)
                    else -> stringResource(R.string.import_add_routine)
                }
                Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                    PrimaryButton(
                        label,
                        actions.onAdd,
                        Modifier.fillMaxWidth().height(60.dp),
                        icon = R.drawable.ic_px_plus,
                        enabled = state.step == ImportUiState.Step.PREVIEW,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasteLink(state: ImportUiState, actions: ImportActions) {
    HelpText(stringResource(R.string.import_paste_help))
    PixelTextField(
        value = state.pasted,
        onValueChange = actions.onPastedChange,
        label = stringResource(R.string.import_paste_label),
        error = if (state.pasteInvalid) stringResource(R.string.import_paste_invalid) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { actions.onOpenPasted() }),
        maxLength = 2000,
    )
    PrimaryButton(stringResource(R.string.import_paste_open), actions.onOpenPasted, Modifier.fillMaxWidth())
}

@Composable
private fun Message(text: String, error: Boolean) {
    Text(
        text,
        style = DinoTheme.type.body.copy(fontSize = 15.sp),
        color = if (error) DinoTheme.colors.danger else DinoTheme.colors.muted,
    )
}

@Composable
private fun Problem(text: String, onRetry: () -> Unit) {
    Message(text, error = false)
    SecondaryButton(stringResource(R.string.action_retry), onRetry, Modifier.fillMaxWidth())
}

@Composable
private fun Preview(content: SharedContent, state: ImportUiState) {
    val colors = DinoTheme.colors
    val routines: List<RoutineTemplate>
    val name: String
    val meta: String
    when (content) {
        is SharedContent.Routine -> {
            routines = listOf(content.routine)
            name = content.routine.name
            meta = pluralStringResource(R.plurals.program_step_exercises, content.routine.items.size, content.routine.items.size)
        }
        is SharedContent.Program -> {
            routines = content.program.routines
            name = content.program.name
            val type = stringResource(if (content.program.type == ProgramType.GYM) R.string.import_type_gym else R.string.import_type_calisthenics)
            val days = content.program.cycle.size
            meta = stringResource(R.string.import_program_meta, type, pluralStringResource(R.plurals.import_days, days, days))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GroupLabel(stringResource(if (content is SharedContent.Program) R.string.import_label_program else R.string.import_label_routine))
        Text(name, style = DinoTheme.type.title.copy(fontSize = 28.sp, lineHeight = 32.sp), color = colors.outline)
        Text(meta, style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.muted)
    }
    TextBox(
        when {
            content is SharedContent.Routine -> stringResource(R.string.import_explain_routine, name)
            state.becomesActive -> stringResource(R.string.import_explain_program_active, name)
            else -> stringResource(R.string.import_explain_program, name)
        },
        showMascot = true,
    )
    if (content is SharedContent.Program) {
        GroupLabel(stringResource(R.string.import_cycle_label))
        CompactCycleStrip(content.program.cycle.map { CycleCell(it?.let { i -> routines[i].name }, isCurrent = false) })
        GroupLabel(stringResource(R.string.import_routines_label))
    }
    routines.forEach { RoutineCard(it, state) }
    if (state.newExercises.isNotEmpty()) HelpText(stringResource(R.string.import_exercises_note))
}

@Composable
private fun RoutineCard(routine: RoutineTemplate, state: ImportUiState) {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, SteppedShape)
            .steppedBorder(colors.outline)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(routine.name, style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 24.sp), color = colors.outline)
        if (routine.items.isEmpty()) HelpText(stringResource(R.string.import_no_exercises))
        routine.items.forEach { ItemRow(it, isNew = it.exercise in state.newExercises) }
    }
}

@Composable
private fun ItemRow(item: RoutineItemTemplate, isNew: Boolean) {
    val colors = DinoTheme.colors
    val meta = if (item.exercise.kind == ExerciseKind.SHOW_ONLY) {
        stringResource(R.string.routine_item_show_only)
    } else {
        stringResource(
            R.string.routine_item_meta,
            WorkoutFormat.setsTimesReps(item.sets, item.repMin, item.repMax),
            WorkoutFormat.clock(item.restSeconds.toLong()),
        )
    }
    val newDescription = stringResource(R.string.import_cd_new)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ExerciseTile(item.exercise.icon, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.exercise.name,
                style = DinoTheme.type.body.copy(fontSize = 15.sp),
                color = colors.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
        }
        if (isNew) {
            Tag(stringResource(R.string.import_new_tag), Modifier.semantics { contentDescription = newDescription })
        }
    }
}
