package io.github.pierorivera1.dinogym.ui.exercises

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.rules.PatternRules
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PixelTextField
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.SwitchRow
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.drawable
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/** The editor's taps and edits. Each is stored as it happens (decision 41). */
data class ExerciseEditActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onIconChange: (ExerciseIcon) -> Unit,
    val onKindChange: (ExerciseKind) -> Unit,
    val onPatternChange: (String) -> Unit,
    val onWeightChange: (Boolean) -> Unit,
    val onArchivedChange: (Boolean) -> Unit,
) {
    companion object {
        /** No-op actions, for screenshot tests. */
        val None = ExerciseEditActions({}, {}, {}, {}, {}, {}, {})
    }
}

/** The icon choices in the order of the mock, each with the name a screen reader says. */
private val IconChoices = listOf(
    ExerciseIcon.LEVER to R.string.exercise_icon_lever,
    ExerciseIcon.PULL_UP to R.string.exercise_icon_pull_up,
    ExerciseIcon.ROW to R.string.exercise_icon_row,
    ExerciseIcon.CURL to R.string.exercise_icon_curl,
    ExerciseIcon.RAISE to R.string.exercise_icon_raise,
    ExerciseIcon.DIP to R.string.exercise_icon_dip,
    ExerciseIcon.PUSH_UP to R.string.exercise_icon_push_up,
    ExerciseIcon.SQUAT to R.string.exercise_icon_squat,
    ExerciseIcon.JUMP_ROPE to R.string.exercise_icon_jump_rope,
    ExerciseIcon.GENERIC to R.string.exercise_icon_generic,
)

/** The kinds in the order of HOW IT IS LOGGED. */
private val KindChoices = listOf(
    ExerciseKind.REPS to R.string.exercise_kind_reps,
    ExerciseKind.TEMPLATE to R.string.exercise_kind_pattern,
    ExerciseKind.SHOW_ONLY to R.string.exercise_kind_show_only,
)

/**
 * The exercise editor (mock `ExerciseEdit.dc.html`): top bar with back; NAME; ICON as a radio
 * grid of the ten type icons; HOW IT IS LOGGED (REPS / PATTERN / SHOW ONLY), with the pattern
 * field and a live preview for PATTERN; the Log weight switch; and ARCHIVE (or RESTORE for an
 * archived exercise) at the bottom with a note of the routines that use it. No bottom bar and
 * no Save button. Stateless: it draws [state] and reports edits through [actions].
 * [focusName] focuses the name field once, for an exercise `+ NEW` just created.
 */
@Composable
fun ExerciseEditScreen(
    state: ExerciseEditUiState,
    actions: ExerciseEditActions,
    modifier: Modifier = Modifier,
    focusName: Boolean = false,
) {
    val colors = DinoTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val viewportHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // At least a screen tall, so ARCHIVE sits at the bottom as in the mock.
                .heightIn(min = viewportHeight)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBar(
                title = stringResource(R.string.exercise_edit_title),
                onBack = actions.onBack,
                backDescription = stringResource(R.string.exercise_edit_back_cd),
            )
            when {
                state.isLoading -> Unit
                state.isMissing -> Text(stringResource(R.string.exercise_missing), style = DinoTheme.type.body, color = colors.muted)
                else -> EditorFields(state, actions, focusName)
            }
        }
    }
}

@Composable
private fun ColumnScope.EditorFields(
    state: ExerciseEditUiState,
    actions: ExerciseEditActions,
    focusName: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    var focusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusName) {
        if (focusName && !focusDone) {
            focusDone = true
            nameFocus.requestFocus()
        }
    }
    val doneKeys = KeyboardActions(onDone = { focusManager.clearFocus() })

    PixelTextField(
        value = state.name,
        onValueChange = actions.onNameChange,
        label = stringResource(R.string.exercise_name_label),
        labelEnd = stringResource(R.string.label_saves_as_you_go),
        error = if (state.nameError) stringResource(R.string.exercise_name_error) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = doneKeys,
        focusRequester = nameFocus,
        selectAllInitially = focusName,
    )

    LabeledGroup(R.string.exercise_icon_label) {
        IconGrid(state.icon, actions.onIconChange)
    }

    LabeledGroup(R.string.exercise_kind_label) {
        SegmentedControl(
            options = KindChoices.map { stringResource(it.second) },
            selectedIndex = KindChoices.indexOfFirst { it.first == state.kind },
            onSelect = { actions.onKindChange(KindChoices[it].first) },
        )
    }
    when (state.kind) {
        ExerciseKind.TEMPLATE -> PatternField(state, actions.onPatternChange, doneKeys)
        ExerciseKind.SHOW_ONLY -> HelpText(stringResource(R.string.exercise_kind_show_only_help))
        ExerciseKind.REPS -> Unit
    }

    SwitchRow(
        title = stringResource(R.string.exercise_weight),
        checked = state.weightEnabled,
        onCheckedChange = actions.onWeightChange,
        help = stringResource(if (state.weightEnabled) R.string.exercise_weight_help_on else R.string.exercise_weight_help_off),
    )

    Spacer(Modifier.weight(1f))
    ArchiveSection(state, actions.onArchivedChange)
}

/** A `label` with its control 6dp under it, as each group in the mock. */
@Composable
private fun LabeledGroup(@StringRes label: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label), style = DinoTheme.type.label.copy(fontSize = 13.sp), color = DinoTheme.colors.muted)
        content()
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = DinoTheme.colors.muted)
}

/**
 * The ten icons as a radio group, five per row. The selected one uses the selected-state
 * colours and looks pressed; the others are raised `surface2` boxes.
 */
@Composable
private fun IconGrid(selected: ExerciseIcon, onSelect: (ExerciseIcon) -> Unit) {
    val colors = DinoTheme.colors
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconChoices.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (icon, name) ->
                    val isSelected = icon == selected
                    val description = stringResource(name)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(SteppedShape)
                            .background(if (isSelected) colors.selBg else colors.surface2)
                            .then(if (isSelected) Modifier else Modifier.raised(colors, pressed = false))
                            .steppedBorder(if (isSelected) colors.selBorder else colors.surface2)
                            .selectable(
                                selected = isSelected,
                                interactionSource = null,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { onSelect(icon) },
                            )
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(icon.drawable(), if (isSelected) colors.selText else colors.outline)
                    }
                }
            }
        }
    }
}

/** PATTERN: the template field, then "{n} is the number you log. Shows as: **3s + 4 pulls + 3s**". */
@Composable
private fun PatternField(state: ExerciseEditUiState, onChange: (String) -> Unit, keys: KeyboardActions) {
    val colors = DinoTheme.colors
    // The field and its preview are one group in the mock, 6dp apart.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelTextField(
            value = state.pattern,
            onValueChange = onChange,
            label = stringResource(R.string.exercise_pattern_label),
            error = if (state.patternError) stringResource(R.string.exercise_pattern_error) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = keys,
        )
        if (!state.patternError) {
            val help = stringResource(R.string.exercise_pattern_help)
            Text(
                text = buildAnnotatedString {
                    append(help)
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.outline)) {
                        append(PatternRules.preview(state.pattern, ExerciseEditRules.PREVIEW_NUMBER))
                    }
                },
                style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.muted,
            )
        }
    }
}

/** ARCHIVE (or RESTORE) and the note about which routines use the exercise. */
@Composable
private fun ArchiveSection(state: ExerciseEditUiState, onArchivedChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SecondaryButton(
            text = stringResource(if (state.archived) R.string.exercise_restore else R.string.exercise_archive),
            onClick = { onArchivedChange(!state.archived) },
            modifier = Modifier.fillMaxWidth(),
            icon = R.drawable.ic_px_archive,
        )
        val names = state.usedIn
        val usage = when (names.size) {
            0 -> stringResource(R.string.exercise_used_nowhere)
            1 -> stringResource(R.string.exercise_used_in, names[0])
            // "Pull, Push and Upper": commas, then the language's "and" before the last name.
            else -> stringResource(
                R.string.exercise_used_in,
                stringResource(R.string.exercise_list_and, names.dropLast(1).joinToString(", "), names.last()),
            )
        }
        val help = stringResource(if (state.archived) R.string.exercise_archived_help else R.string.exercise_archive_help)
        HelpText("$usage $help")
    }
}
