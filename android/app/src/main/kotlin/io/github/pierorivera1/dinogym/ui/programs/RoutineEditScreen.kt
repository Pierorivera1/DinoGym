package io.github.pierorivera1.dinogym.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.ui.components.ConfirmSheet
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.ReorderItemScope
import io.github.pierorivera1.dinogym.ui.components.ReorderableColumn
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.UndoBar
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import io.github.pierorivera1.dinogym.ui.workout.RestPickerSheet
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat

/** The routine editor's taps and edits. Each is stored as it happens (decision 41). */
data class RoutineEditActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onToggleExpanded: (itemId: String) -> Unit,
    val onSetsChange: (itemId: String, delta: Int) -> Unit,
    val onRangeChange: (itemId: String, range: Boolean) -> Unit,
    val onRepMinChange: (itemId: String, delta: Int) -> Unit,
    val onRepMaxChange: (itemId: String, delta: Int) -> Unit,
    val onOpenRest: (itemId: String) -> Unit,
    val onSaveRest: (itemId: String, seconds: Int) -> Unit,
    val onRemove: (itemId: String) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onAskDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onUndo: () -> Unit,
    val onUndoTimeout: () -> Unit,
    val picker: PickerActions,
) {
    companion object {
        /** No-op actions, for screenshot tests. */
        val None = RoutineEditActions(
            {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, { _, _ -> },
            {}, {}, {}, {}, {}, PickerActions.None,
        )
    }
}

/**
 * The routine editor (mock `RoutineEdit.dc.html`): top bar with back and the danger trash;
 * NAME; EXERCISES with the count and total sets; the exercises as reorderable cards
 * (`sets × reps · rest`). Tapping a card opens it in place: SETS, REPS (FIXED / RANGE),
 * REST (the rest picker) and a trash that removes it from this routine. ADD EXERCISE is fixed
 * at the bottom and opens the exercise picker. Stateless: it draws [state] and reports
 * through [actions].
 */
@Composable
fun RoutineEditScreen(
    state: RoutineEditUiState,
    actions: RoutineEditActions,
    modifier: Modifier = Modifier,
    focusName: Boolean = false,
) {
    val colors = DinoTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = if (state.undo != null) 88.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TopBar(
                    title = stringResource(R.string.routine_edit_title),
                    onBack = actions.onBack,
                    action = if (state.isLoading || state.isMissing) {
                        null
                    } else {
                        {
                            DangerIconButton(
                                stringResource(R.string.routine_delete_cd),
                                actions.onAskDelete,
                                icon = R.drawable.ic_px_trash,
                                iconSize = 24.dp,
                            )
                        }
                    },
                )
                when {
                    state.isLoading -> Unit
                    state.isMissing -> Text(stringResource(R.string.routine_missing), style = DinoTheme.type.body, color = colors.muted)
                    else -> RoutineBody(state, actions, focusName)
                }
            }
            if (!state.isLoading && !state.isMissing) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.bg)
                        .drawBehind { drawRect(colors.surface2, Offset.Zero, Size(size.width, ArtPixel.toPx())) }
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                ) {
                    PrimaryButton(
                        stringResource(R.string.routine_add_exercise),
                        actions.picker.onOpen,
                        Modifier.fillMaxWidth().height(60.dp),
                        icon = R.drawable.ic_px_plus,
                    )
                }
            }
        }
        state.undo?.let { removed ->
            UndoBar(
                message = stringResource(R.string.routine_undo_exercise, removed.name),
                onUndo = actions.onUndo,
                onTimeout = actions.onUndoTimeout,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
            )
        }
        RoutineSheets(state, actions)
    }
}

@Composable
private fun RoutineBody(state: RoutineEditUiState, actions: RoutineEditActions, focusName: Boolean) {
    NameField(
        value = state.name,
        onChange = actions.onNameChange,
        label = stringResource(R.string.routine_name_label),
        labelEnd = stringResource(R.string.label_saves_as_you_go),
        error = if (state.nameError) stringResource(R.string.routine_name_error) else null,
        focus = focusName,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GroupLabel(stringResource(R.string.routine_exercises_label, state.items.size))
            GroupLabel(pluralStringResource(R.plurals.routine_sets_meta, state.totalSets, state.totalSets))
        }
        if (state.items.isEmpty()) {
            HelpText(stringResource(R.string.routine_empty))
        } else {
            ReorderableColumn(
                items = state.items,
                key = { it.id },
                onMove = actions.onMove,
                spacing = 6.dp,
            ) { _, item ->
                if (item.id == state.expandedId) {
                    OpenCard(item, actions)
                } else {
                    ClosedCard(item, actions)
                }
            }
        }
    }
}

/** "2 × 4 · rest 2:30", or the show-only note. */
@Composable
private fun itemMeta(item: RoutineItemUi): String = if (item.kind == ExerciseKind.SHOW_ONLY) {
    stringResource(R.string.routine_item_show_only)
} else {
    stringResource(
        R.string.routine_item_meta,
        WorkoutFormat.setsTimesReps(item.target.sets, item.target.repMin, item.target.repMax),
        WorkoutFormat.clock(item.target.restSeconds.toLong()),
    )
}

/** The 40dp drag handle of a card. */
@Composable
private fun ReorderItemScope.Handle(name: String, height: Int) {
    Box(
        Modifier
            .width(40.dp)
            .height(height.dp)
            .dragHandle(stringResource(R.string.routine_cd_drag, name)),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(R.drawable.ic_px_drag, DinoTheme.colors.muted, size = 24.dp)
    }
}

/** A closed card: raised `surface`, handle, icon tile, name and meta, a down chevron. */
@Composable
private fun ReorderItemScope.ClosedCard(item: RoutineItemUi, actions: RoutineEditActions) {
    val colors = DinoTheme.colors
    val meta = itemMeta(item)
    val description = stringResource(R.string.routine_cd_open, item.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (isDragging) Modifier.hardShadow(colors.shadow, SteppedShape) else Modifier)
            .clip(SteppedShape)
            .background(colors.surface)
            .raised(colors, pressed = false)
            .steppedBorder(colors.outline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Handle(item.name, 56)
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { actions.onToggleExpanded(item.id) }
                .clearAndSetSemantics { contentDescription = "$description. $meta" }
                .padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseTile(item.icon, size = 40.dp, iconSize = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    style = DinoTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = DinoTheme.type.body.copy(fontSize = 13.sp), color = colors.muted)
            }
            PixelIcon(R.drawable.ic_px_chevron, colors.outline, Modifier.rotate(90f), size = 24.dp)
        }
    }
}

/**
 * The open card: selected-state colours and the hard shadow; header with the handle, icon,
 * name, the danger trash and the close (^) button; then SETS, REPS and REST.
 */
@Composable
private fun ReorderItemScope.OpenCard(item: RoutineItemUi, actions: RoutineEditActions) {
    val colors = DinoTheme.colors
    val labelColor = if (colors.isNight) colors.muted else colors.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .hardShadow(colors.shadow, SteppedShape)
            .clip(SteppedShape)
            .background(colors.selBg)
            .steppedBorder(colors.selBorder)
            .padding(top = 2.dp, end = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Handle(item.name, 52)
            ExerciseTile(item.icon, size = 40.dp, iconSize = 32.dp)
            Text(
                item.name,
                style = DinoTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DangerIconButton(
                stringResource(R.string.routine_cd_remove, item.name),
                { actions.onRemove(item.id) },
                icon = R.drawable.ic_px_trash,
                iconSize = 24.dp,
            )
            val close = stringResource(R.string.routine_cd_close, item.name)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(SteppedShape)
                    .background(colors.surface)
                    .steppedBorder(colors.outline)
                    .clickable(role = Role.Button) { actions.onToggleExpanded(item.id) }
                    .semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(R.drawable.ic_px_chevron, colors.outline, Modifier.rotate(-90f), size = 24.dp)
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.kind == ExerciseKind.SHOW_ONLY) {
                Text(
                    stringResource(R.string.routine_show_only_help),
                    style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = labelColor,
                )
            } else {
                TargetControls(item, actions, labelColor)
            }
        }
    }
}

@Composable
private fun TargetControls(item: RoutineItemUi, actions: RoutineEditActions, labelColor: Color) {
    val t = item.target
    ControlRow(stringResource(R.string.routine_sets), labelColor, caps = true) {
        MiniStepper(
            t.sets,
            { actions.onSetsChange(item.id, -1) },
            { actions.onSetsChange(item.id, 1) },
            stringResource(R.string.routine_cd_less_set),
            stringResource(R.string.routine_cd_more_set),
        )
    }
    ControlRow(stringResource(R.string.routine_reps), labelColor, caps = true) {
        SegmentedControl(
            options = listOf(stringResource(R.string.routine_reps_fixed), stringResource(R.string.routine_reps_range)),
            selectedIndex = if (t.isRange) 1 else 0,
            onSelect = { actions.onRangeChange(item.id, it == 1) },
            modifier = Modifier.width(200.dp),
        )
    }
    if (t.isRange) {
        ControlRow(stringResource(R.string.routine_reps_from), labelColor) {
            MiniStepper(
                t.repMin,
                { actions.onRepMinChange(item.id, -1) },
                { actions.onRepMinChange(item.id, 1) },
                stringResource(R.string.routine_cd_less_from),
                stringResource(R.string.routine_cd_more_from),
            )
        }
        ControlRow(stringResource(R.string.routine_reps_to), labelColor) {
            MiniStepper(
                t.repMax,
                { actions.onRepMaxChange(item.id, -1) },
                { actions.onRepMaxChange(item.id, 1) },
                stringResource(R.string.routine_cd_less_to),
                stringResource(R.string.routine_cd_more_to),
            )
        }
    } else {
        ControlRow(stringResource(R.string.routine_reps_target), labelColor) {
            MiniStepper(
                t.repMin,
                { actions.onRepMinChange(item.id, -1) },
                { actions.onRepMinChange(item.id, 1) },
                stringResource(R.string.cd_one_less_rep),
                stringResource(R.string.cd_one_more_rep),
            )
        }
    }
    val rest = WorkoutFormat.clock(t.restSeconds.toLong())
    val restDescription = stringResource(R.string.cd_rest_time, rest)
    ControlRow(stringResource(R.string.routine_rest), labelColor, caps = true) {
        SecondaryButton(
            rest,
            { actions.onOpenRest(item.id) },
            Modifier.width(148.dp).height(48.dp).semantics { contentDescription = restDescription },
            icon = R.drawable.ic_px_timer,
        )
    }
}

/** A label on the left and its control on the right; [caps] uses the `label` style. */
@Composable
private fun ControlRow(label: String, color: Color, caps: Boolean = false, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (caps) DinoTheme.type.label.copy(fontSize = 13.sp) else DinoTheme.type.body.copy(fontSize = 13.sp),
            color = color,
        )
        control()
    }
}

/** The card's stepper: 48dp − and + around the number in Pixelify 30sp. */
@Composable
private fun MiniStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit, less: String, more: String) {
    val colors = DinoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        RaisedBox(onDecrease, colors.surface, colors.outline, Modifier.size(48.dp).semantics { contentDescription = less }) {
            PixelIcon(R.drawable.ic_px_minus, colors.outline, size = 24.dp)
        }
        Text(
            value.toString(),
            style = DinoTheme.type.title.copy(fontSize = 30.sp, lineHeight = 32.sp),
            color = colors.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp).semantics { stateDescription = value.toString() },
        )
        RaisedBox(onIncrease, colors.surface, colors.outline, Modifier.size(48.dp).semantics { contentDescription = more }) {
            PixelIcon(R.drawable.ic_px_plus, colors.outline, size = 24.dp)
        }
    }
}

@Composable
private fun RoutineSheets(state: RoutineEditUiState, actions: RoutineEditActions) {
    when (val sheet = state.sheet) {
        is RoutineSheet.Rest -> RestPickerSheet(
            exerciseName = sheet.exerciseName,
            routineName = state.savedName,
            initialSeconds = sheet.seconds,
            onSave = { actions.onSaveRest(sheet.itemId, it) },
            onClose = actions.onCloseSheet,
        )
        is RoutineSheet.ConfirmDelete -> ConfirmSheet(
            title = stringResource(R.string.routine_delete_title),
            message = if (sheet.programNames.isEmpty()) {
                stringResource(R.string.routine_delete_body, state.savedName)
            } else {
                stringResource(R.string.routine_delete_body_used, state.savedName, joinNames(sheet.programNames))
            },
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onCloseSheet,
        )
        RoutineSheet.DeleteBlocked -> WindowSheet(
            stringResource(R.string.routine_delete_blocked_title),
            actions.onCloseSheet,
            stringResource(R.string.cd_close),
        ) {
            Text(
                stringResource(R.string.routine_delete_blocked_body, state.savedName),
                style = DinoTheme.type.body.copy(fontSize = 15.sp),
                color = DinoTheme.colors.outline,
            )
            SecondaryButton(stringResource(R.string.action_ok), actions.onCloseSheet, Modifier.fillMaxWidth())
        }
        null -> Unit
    }
    state.picker?.let { ExercisePickerSheet(it, state.savedName, actions.picker) }
}

/** "My program", "My program and Short week", "A, B and C". */
@Composable
private fun joinNames(names: List<String>): String =
    if (names.size == 1) names[0] else stringResource(R.string.programs_list_and, names.dropLast(1).joinToString(", "), names.last())
