package io.github.pierorivera1.dinogym.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.PickerWheel
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.ScrollPicker
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.Stepper
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/** Weight changes in plate steps: 2.5 kg. */
internal const val WEIGHT_STEP_KG = 2.5

/**
 * The ✕ sheet, shared by the active exercise and the rest screen: SAVE AND FINISH (only when
 * something was logged), DISCARD, or KEEP GOING.
 */
@Composable
fun EndWorkoutSheet(
    loggedSetCount: Int,
    onSaveAndFinish: () -> Unit,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val colors = DinoTheme.colors
    WindowSheet(stringResource(R.string.end_title), onKeepGoing, stringResource(R.string.cd_close)) {
        Text(
            text = if (loggedSetCount > 0) {
                pluralStringResource(R.plurals.end_body, loggedSetCount, loggedSetCount)
            } else {
                stringResource(R.string.end_body_empty)
            },
            style = DinoTheme.type.body.copy(fontSize = 15.sp),
            color = colors.outline,
        )
        if (loggedSetCount > 0) {
            PrimaryButton(
                stringResource(R.string.end_save),
                onSaveAndFinish,
                Modifier.fillMaxWidth().height(60.dp),
                icon = R.drawable.ic_px_check,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerTextButton(stringResource(R.string.end_discard), onDiscard, Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.end_keep_going), onKeepGoing, Modifier.weight(1f))
        }
    }
}

/** A text button with the `danger` border and text (DISCARD, DELETE). Not raised, like the ✕. */
@Composable
internal fun DangerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    RaisedBox(onClick, colors.surface, colors.danger, modifier.height(56.dp)) {
        Text(text, style = DinoTheme.type.button, color = colors.danger, maxLines = 1)
    }
}

/** The NOTE sheet: a text field for the set about to be logged. */
@Composable
fun NoteSheet(initialText: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val colors = DinoTheme.colors
    var text by rememberSaveable { mutableStateOf(initialText) }
    val title = stringResource(R.string.note_title)
    WindowSheet(title, onClose, stringResource(R.string.cd_close)) {
        Text(stringResource(R.string.note_hint), style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(200) },
            textStyle = DinoTheme.type.body.copy(color = colors.outline),
            cursorBrush = SolidColor(colors.outline),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(SteppedShape)
                .background(colors.bg)
                .steppedBorder(colors.outline)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .semantics { contentDescription = title },
        )
        PrimaryButton(stringResource(R.string.action_save), { onSave(text) }, Modifier.fillMaxWidth().height(60.dp))
    }
}

/** Correct or delete a logged set: steppers like the active set card, then SAVE or DELETE. */
@Composable
fun EditSetSheet(sheet: WorkoutSheet.EditSet, onSave: (Int, Double?) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var reps by rememberSaveable(sheet.setId) { mutableIntStateOf(sheet.reps) }
    var weight by rememberSaveable(sheet.setId) { mutableDoubleStateOf(sheet.weightKg ?: 0.0) }
    val locale = LocalConfiguration.current.locales[0]
    WindowSheet(stringResource(R.string.workout_set_label, sheet.setIndex), onClose, stringResource(R.string.cd_close)) {
        Stepper(
            label = stringResource(R.string.label_reps),
            value = reps,
            onDecrease = { reps = (reps - 1).coerceAtLeast(0) },
            onIncrease = { reps++ },
            decreaseDescription = stringResource(R.string.cd_one_less_rep),
            increaseDescription = stringResource(R.string.cd_one_more_rep),
            modifier = Modifier.fillMaxWidth(),
        )
        if (sheet.weightKg != null) {
            Stepper(
                label = stringResource(R.string.label_kg),
                value = weight.toInt(),
                valueText = WorkoutFormat.kg(weight, locale),
                onDecrease = { weight = (weight - WEIGHT_STEP_KG).coerceAtLeast(0.0) },
                onIncrease = { weight += WEIGHT_STEP_KG },
                decreaseDescription = stringResource(R.string.cd_less_kg),
                increaseDescription = stringResource(R.string.cd_more_kg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DangerTextButton(stringResource(R.string.action_delete), onDelete, Modifier.weight(1f))
            PrimaryButton(
                stringResource(R.string.action_save),
                { onSave(reps, sheet.weightKg?.let { weight }) },
                Modifier.weight(2f),
            )
        }
    }
}

/**
 * The rest time picker (mock `RestPicker.dc.html`): two wheels (minutes 0–9, seconds in 5 s
 * steps, at most 9:00), the 1:30 and 2:30 shortcuts, and SAVE. The wheels' values live here
 * until SAVE; [onSave] gets the seconds.
 */
@Composable
fun RestPickerSheet(
    exerciseName: String,
    routineName: String,
    initialSeconds: Int,
    onSave: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = DinoTheme.colors
    val start = WorkoutRules.pickerIndexes(initialSeconds)
    var minute by rememberSaveable { mutableIntStateOf(start.first) }
    var second by rememberSaveable { mutableIntStateOf(start.second) }
    WindowSheet(stringResource(R.string.picker_title), onClose, stringResource(R.string.cd_close)) {
        Text(
            stringResource(R.string.picker_scope, exerciseName, routineName),
            style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.muted,
        )
        val minLabel = stringResource(R.string.label_min)
        val secLabel = stringResource(R.string.label_sec)
        ScrollPicker(labels = listOf(minLabel, secLabel)) {
            PickerWheel(
                label = minLabel,
                values = WorkoutRules.MINUTE_STEPS.map { it.toString() },
                selectedIndex = minute,
                onSelect = { minute = it },
                modifier = Modifier.weight(1f),
            )
            PickerWheel(
                label = secLabel,
                values = WorkoutRules.SECOND_STEPS.map { it.toString().padStart(2, '0') },
                selectedIndex = second,
                onSelect = { second = it },
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.label_defaults),
                style = DinoTheme.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetButton(90, stringResource(R.string.picker_isolation), Modifier.weight(1f)) {
                    WorkoutRules.pickerIndexes(90).let { minute = it.first; second = it.second }
                }
                PresetButton(150, stringResource(R.string.picker_compound), Modifier.weight(1f)) {
                    WorkoutRules.pickerIndexes(150).let { minute = it.first; second = it.second }
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.action_save),
            { onSave(WorkoutRules.pickerSeconds(minute, second)) },
            Modifier.fillMaxWidth().height(60.dp),
        )
    }
}

/** A shortcut such as "**1:30** · isolation". */
@Composable
private fun PresetButton(seconds: Int, kind: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    RaisedBox(onClick, colors.surface, colors.action, modifier.height(52.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(WorkoutFormat.clock(seconds.toLong()), style = DinoTheme.type.button, color = colors.outline)
            Text(
                " · $kind",
                style = DinoTheme.type.body.copy(fontSize = 14.sp),
                color = colors.outline,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
