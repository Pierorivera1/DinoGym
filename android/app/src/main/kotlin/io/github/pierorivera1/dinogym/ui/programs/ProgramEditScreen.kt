package io.github.pierorivera1.dinogym.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.ui.components.ConfirmSheet
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PixelTextField
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.ReorderableColumn
import io.github.pierorivera1.dinogym.ui.components.ReorderableRow
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.Tag
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.UndoBar
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.share.ShareButton
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/** The program editor's taps and edits. Each is stored as it happens (decision 41). */
data class ProgramEditActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onTypeChange: (ProgramType) -> Unit,
    val onActivate: () -> Unit,
    val onOpenRoutine: (routineId: String) -> Unit,
    val onRemoveStep: (stepId: String) -> Unit,
    val onMoveStep: (from: Int, to: Int) -> Unit,
    val onOpenAddRoutine: () -> Unit,
    val onAddRoutineStep: (routineId: String) -> Unit,
    val onNewRoutineStep: () -> Unit,
    val onAddRestDay: () -> Unit,
    val onAskDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onUndo: () -> Unit,
    val onUndoTimeout: () -> Unit,
    /** SHARE PROGRAM under the cycle (task 23). */
    val onShare: () -> Unit = {},
) {
    companion object {
        /** No-op actions, for screenshot tests. */
        val None = ProgramEditActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

private val TypeChoices = listOf(ProgramType.GYM, ProgramType.CALISTHENICS)

/**
 * The program editor (mock `ProgramEdit.dc.html`): top bar with back and the danger trash;
 * the ACTIVE tag and "Changes save as you go."; NAME; TYPE (Gym / Calisthenics); CYCLE as
 * reorderable rows (step number, routine name and exercise count with a NEXT tag, or a rest
 * day), `+ ROUTINE` and `+ REST DAY`; and, for an inactive program, USE THIS PROGRAM at the
 * bottom. Over it: the UNDO bar after a step is removed, and the `+ ROUTINE` and delete sheets.
 * Stateless: it draws [state] and reports through [actions].
 */
@Composable
fun ProgramEditScreen(
    state: ProgramEditUiState,
    actions: ProgramEditActions,
    modifier: Modifier = Modifier,
    focusName: Boolean = false,
) {
    val colors = DinoTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    // Room under the last row for the UNDO bar.
                    .padding(start = 16.dp, end = 16.dp, bottom = if (state.undo != null) 88.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TopBar(
                    title = stringResource(R.string.program_edit_title),
                    onBack = actions.onBack,
                    backDescription = stringResource(R.string.program_edit_back_cd),
                    action = if (state.isLoading || state.isMissing) {
                        null
                    } else {
                        {
                            DangerIconButton(
                                stringResource(R.string.program_delete_cd),
                                actions.onAskDelete,
                                icon = R.drawable.ic_px_trash,
                                iconSize = 24.dp,
                            )
                        }
                    },
                )
                when {
                    state.isLoading -> Unit
                    state.isMissing -> Text(stringResource(R.string.program_missing), style = DinoTheme.type.body, color = colors.muted)
                    else -> EditorBody(state, actions, focusName)
                }
            }
        }
        state.undo?.let { removed ->
            UndoBar(
                message = stringResource(R.string.program_undo_step, removed.label ?: stringResource(R.string.program_rest_day)),
                onUndo = actions.onUndo,
                onTimeout = actions.onUndoTimeout,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
        when (state.sheet) {
            ProgramSheet.AddRoutine -> AddRoutineSheet(state.routines, actions)
            ProgramSheet.ConfirmDelete -> ConfirmSheet(
                title = stringResource(R.string.program_delete_title),
                message = stringResource(
                    if (state.isActive) R.string.program_delete_body_active else R.string.program_delete_body,
                    state.name,
                ),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onCloseSheet,
            )
            null -> Unit
        }
    }
}

@Composable
private fun ColumnScope.EditorBody(
    state: ProgramEditUiState,
    actions: ProgramEditActions,
    focusName: Boolean,
) {
    val colors = DinoTheme.colors
    val focusManager = LocalFocusManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.isActive) Tag(stringResource(R.string.programs_active))
        Text(stringResource(R.string.program_saves), style = DinoTheme.type.body.copy(fontSize = 13.sp), color = colors.muted)
    }

    NameField(
        value = state.name,
        onChange = actions.onNameChange,
        label = stringResource(R.string.program_name_label),
        error = if (state.nameError) stringResource(R.string.program_name_error) else null,
        focus = focusName,
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupLabel(stringResource(R.string.program_type_label))
        SegmentedControl(
            options = listOf(stringResource(R.string.program_type_gym), stringResource(R.string.program_type_calisthenics)),
            selectedIndex = TypeChoices.indexOf(state.type),
            onSelect = {
                focusManager.clearFocus()
                actions.onTypeChange(TypeChoices[it])
            },
        )
        HelpText(stringResource(R.string.program_type_help))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GroupLabel(stringResource(R.string.program_cycle_label))
            if (state.steps.isNotEmpty()) {
                GroupLabel(pluralStringResource(R.plurals.program_cycle_meta, state.steps.size, state.steps.size))
            }
        }
        if (state.steps.isEmpty()) {
            HelpText(stringResource(R.string.program_cycle_empty))
        } else {
            CycleList(state.steps, actions)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(
            stringResource(R.string.program_add_routine),
            actions.onOpenAddRoutine,
            Modifier.weight(1f),
            icon = R.drawable.ic_px_plus,
        )
        SecondaryButton(
            stringResource(R.string.program_add_rest),
            actions.onAddRestDay,
            Modifier.weight(1f),
            icon = R.drawable.ic_px_plus,
        )
    }
    HelpText(stringResource(R.string.program_drag_help))
    ShareButton(isProgram = true, onClick = actions.onShare)

    if (!state.isActive) {
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            stringResource(R.string.program_use),
            actions.onActivate,
            Modifier.fillMaxWidth().height(60.dp),
            icon = R.drawable.ic_px_check,
        )
    }
}

/** The cycle as one bordered `surface` box of reorderable rows with dotted dividers. */
@Composable
private fun CycleList(steps: List<CycleStepUi>, actions: ProgramEditActions) {
    val colors = DinoTheme.colors
    val restDay = stringResource(R.string.program_rest_day)
    ReorderableColumn(
        items = steps,
        key = { it.id },
        onMove = actions.onMoveStep,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline)
            .padding(ArtPixel),
    ) { index, step ->
        val number = index + 1
        val label = step.routineName ?: restDay
        ReorderableRow(
            dragDescription = stringResource(R.string.program_cd_drag_step, number, label),
            onRemove = { actions.onRemoveStep(step.id) },
            removeDescription = stringResource(R.string.program_cd_remove_step, number, label),
            modifier = if (index < steps.lastIndex && !isDragging) Modifier.dottedBottomOver(colors.surface2) else Modifier,
        ) {
            Text(
                number.toString(),
                style = DinoTheme.type.button.copy(fontSize = 15.sp),
                color = colors.muted,
                modifier = Modifier.width(22.dp),
            )
            if (step.routineId != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { actions.onOpenRoutine(step.routineId) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label.uppercase(),
                                style = DinoTheme.type.title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                                color = colors.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (step.isNext) Tag(stringResource(R.string.label_next))
                        }
                        Text(
                            pluralStringResource(R.plurals.program_step_exercises, step.exerciseCount, step.exerciseCount),
                            style = DinoTheme.type.body.copy(fontSize = 13.sp),
                            color = colors.muted,
                        )
                    }
                    PixelIcon(R.drawable.ic_px_chevron, colors.outline, size = 20.dp)
                }
            } else {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(R.drawable.ic_px_zzz, colors.muted, size = 24.dp)
                    Text(restDay, style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.muted)
                    if (step.isNext) Tag(stringResource(R.string.label_next))
                }
            }
        }
    }
}

/**
 * `+ ROUTINE`: a sheet listing every routine (tap to add it as the last step) and NEW
 * ROUTINE, which creates one, adds it and opens its editor.
 */
@Composable
private fun AddRoutineSheet(routines: List<RoutineChoice>, actions: ProgramEditActions) {
    val colors = DinoTheme.colors
    WindowSheet(stringResource(R.string.program_pick_routine_title), actions.onCloseSheet, stringResource(R.string.cd_close)) {
        HelpText(stringResource(R.string.program_pick_routine_help))
        if (routines.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(SteppedShape)
                    .background(colors.surface)
                    .steppedBorder(colors.outline)
                    .padding(ArtPixel)
                    .verticalScroll(rememberScrollState()),
            ) {
                routines.forEachIndexed { index, routine ->
                    val exercises = pluralStringResource(R.plurals.program_step_exercises, routine.exerciseCount, routine.exerciseCount)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .then(if (index < routines.lastIndex) Modifier.dottedBottomOver(colors.surface2) else Modifier)
                            .clickable(role = Role.Button) { actions.onAddRoutineStep(routine.id) }
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelIcon(R.drawable.ic_px_plus, colors.action, size = 24.dp)
                        Text(
                            routine.name.uppercase(),
                            style = DinoTheme.type.title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                            color = colors.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(exercises, style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
                    }
                }
            }
        }
        SecondaryButton(
            stringResource(R.string.program_pick_new_routine),
            actions.onNewRoutineStep,
            Modifier.fillMaxWidth(),
            icon = R.drawable.ic_px_plus,
        )
    }
}

/** A name field that focuses itself once with its text selected, for a row `+ NEW` just made. */
@Composable
internal fun NameField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    focus: Boolean,
    labelEnd: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focus) {
        if (focus && !done) {
            done = true
            requester.requestFocus()
        }
    }
    PixelTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        labelEnd = labelEnd,
        error = error,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        focusRequester = requester,
        selectAllInitially = focus,
    )
}

@Composable
internal fun GroupLabel(text: String) {
    Text(text, style = DinoTheme.type.label.copy(fontSize = 13.sp), color = DinoTheme.colors.muted)
}

@Composable
internal fun HelpText(text: String) {
    Text(text, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = DinoTheme.colors.muted)
}

/**
 * A dotted divider drawn over the row's own background (the reorderable row fills itself,
 * which would hide a line drawn behind it).
 */
internal fun Modifier.dottedBottomOver(color: Color): Modifier = drawWithContent {
    drawContent()
    val dot = ArtPixel.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(color, Offset(x, size.height - dot), Size(dot, dot))
        x += dot * 2
    }
}
