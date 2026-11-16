package io.github.pierorivera1.dinogym.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.PixelCheckbox
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PixelTextField
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.Tag
import io.github.pierorivera1.dinogym.ui.components.TagStyle
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/** The exercise picker's taps. */
data class PickerActions(
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onToggle: (exerciseId: String) -> Unit,
    val onNewExercise: () -> Unit,
    val onAdd: () -> Unit,
) {
    companion object {
        val None = PickerActions({}, {}, {}, {}, {}, {})
    }
}

/**
 * The exercise picker (mock `ExercisePicker.dc.html`): a full-height window sheet over the
 * routine editor. "Adding to the Pull routine", the search field, the result count, then the
 * results as checkbox rows (icon, name, how it is logged, IN ROUTINE). Several can be ticked
 * and added at once with ADD n EXERCISES. `+ NEW EXERCISE "<query>"` opens the exercise
 * editor; coming back, the new exercise is ticked. Archived exercises never appear.
 */
@Composable
fun ExercisePickerSheet(state: PickerUiState, routineName: String, actions: PickerActions) {
    val colors = DinoTheme.colors
    val focusManager = LocalFocusManager.current
    WindowSheet(stringResource(R.string.picker_add_title), actions.onClose, stringResource(R.string.cd_close), fullHeight = true) {
        Text(stringResource(R.string.picker_add_scope, routineName), style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
        PixelTextField(
            value = state.query,
            onValueChange = actions.onQueryChange,
            contentDescription = stringResource(R.string.picker_search_cd),
            leadingIcon = R.drawable.ic_px_search,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            trailing = if (state.query.isEmpty()) {
                null
            } else {
                {
                    val clear = stringResource(R.string.picker_clear_cd)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(role = Role.Button) { actions.onQueryChange("") }
                            .semantics { contentDescription = clear },
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(R.drawable.ic_px_close, colors.muted, size = 24.dp)
                    }
                }
            },
        )
        // The list takes the room left, and shrinks to its rows when they are few.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupLabel(pluralStringResource(R.plurals.picker_results, state.rows.size, state.rows.size))
            if (state.rows.isEmpty()) {
                HelpText(stringResource(R.string.picker_no_results))
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .clip(SteppedShape)
                        .background(colors.surface)
                        .steppedBorder(colors.outline)
                        .padding(ArtPixel),
                ) {
                    itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                        PickerRowItem(row, isLast = index == state.rows.lastIndex, onToggle = { actions.onToggle(row.id) })
                    }
                }
            }
            SecondaryButton(
                text = if (state.query.isBlank()) {
                    stringResource(R.string.picker_new_exercise)
                } else {
                    stringResource(R.string.picker_new_exercise_named, state.query.trim())
                },
                onClick = actions.onNewExercise,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                icon = R.drawable.ic_px_plus,
            )
            HelpText(stringResource(R.string.picker_new_help))
        }
        if (state.selectedCount > 0) {
            PrimaryButton(
                pluralStringResource(R.plurals.picker_add, state.selectedCount, state.selectedCount),
                actions.onAdd,
                Modifier.fillMaxWidth().height(60.dp),
                icon = R.drawable.ic_px_plus,
            )
        } else {
            HelpText(stringResource(R.string.picker_tick_some))
        }
    }
}

/** One checkbox row; the whole row is the target. Ticked rows get the `select` band. */
@Composable
private fun PickerRowItem(row: PickerRow, isLast: Boolean, onToggle: () -> Unit) {
    val colors = DinoTheme.colors
    val band = if (colors.isNight) colors.select.copy(alpha = 0.10f) else colors.select
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .background(if (row.checked) band else colors.surface)
            .dottedBottomOver(if (isLast) colors.surface else colors.surface2)
            .toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseTile(row.icon, size = 48.dp, iconSize = 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.name, style = DinoTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.outline)
            Text(
                when (row.kind) {
                    ExerciseKind.REPS -> stringResource(R.string.picker_kind_reps)
                    ExerciseKind.TEMPLATE -> stringResource(R.string.picker_kind_pattern, row.template.orEmpty())
                    ExerciseKind.SHOW_ONLY -> stringResource(R.string.picker_kind_show_only)
                },
                style = DinoTheme.type.body.copy(fontSize = 13.sp),
                color = if (row.checked && !colors.isNight) colors.outline else colors.muted,
            )
            if (row.inRoutine) Tag(stringResource(R.string.picker_in_routine), style = TagStyle.Outlined)
        }
        PixelCheckbox(row.checked)
    }
}
