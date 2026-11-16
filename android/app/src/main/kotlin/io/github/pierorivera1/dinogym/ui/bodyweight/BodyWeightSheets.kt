package io.github.pierorivera1.dinogym.ui.bodyweight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.DangerButton
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.home.shortDate
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import java.util.Locale

/**
 * LOG WEIGHT (date stepper, kg, SAVE) or GOAL WEIGHT (kg, SAVE GOAL, CLEAR GOAL when one is
 * set), as a window sheet. The kg can be typed (`78.3` or `78,3`) or nudged by 0.1 with − and +.
 */
@Composable
internal fun WeightSheetView(sheet: WeightSheet, locale: Locale, actions: BodyWeightActions) {
    val colors = DinoTheme.colors
    val title = stringResource(if (sheet is WeightSheet.Log) R.string.bw_sheet_log_title else R.string.bw_sheet_goal_title)
    WindowSheet(title = title, onClose = actions.onCloseSheet, closeDescription = stringResource(R.string.cd_close)) {
        if (sheet is WeightSheet.Log) DateStepper(sheet, locale, actions)
        KgRow(sheet, actions)
        val error = if (sheet.showError) stringResource(R.string.bw_kg_error) else null
        Text(
            error ?: stringResource(if (sheet is WeightSheet.Log) R.string.bw_log_help else R.string.bw_goal_help),
            style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = if (error != null) colors.danger else colors.muted,
        )
        PrimaryButton(
            text = stringResource(if (sheet is WeightSheet.Log) R.string.action_save else R.string.bw_save_goal),
            onClick = actions.onSave,
            modifier = Modifier.fillMaxWidth().height(60.dp),
        )
        if (sheet is WeightSheet.Goal && sheet.hasGoal) {
            DangerButton(stringResource(R.string.bw_clear_goal), actions.onClearGoal, Modifier.fillMaxWidth())
        }
    }
}

/** DATE: ‹ day › with today as the default and no way into the future. */
@Composable
private fun DateStepper(sheet: WeightSheet.Log, locale: Locale, actions: BodyWeightActions) {
    val colors = DinoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.bw_date_label), style = DinoTheme.type.label.copy(fontSize = 13.sp), color = colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val previous = stringResource(R.string.bw_prev_day_cd)
            RaisedBox(
                onClick = actions.onPreviousDay,
                fill = colors.surface,
                border = colors.action,
                modifier = Modifier.size(48.dp).semantics { contentDescription = previous },
            ) { PixelIcon(R.drawable.ic_px_chevron, colors.action, Modifier.rotate(180f), size = 24.dp) }

            val isToday = sheet.day == sheet.today
            Column(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(SteppedShape)
                    .background(colors.bg)
                    .steppedBorder(colors.outline),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val date = shortDate(sheet.day, locale)
                Text(
                    if (isToday) stringResource(R.string.bw_today) else date,
                    style = DinoTheme.type.button.copy(fontSize = 18.sp, lineHeight = 20.sp),
                    color = colors.outline,
                )
                if (isToday) Text(date, style = DinoTheme.type.body.copy(fontSize = 12.sp, lineHeight = 14.sp), color = colors.muted)
            }

            val next = stringResource(R.string.bw_next_day_cd)
            if (sheet.canGoForward) {
                RaisedBox(
                    onClick = actions.onNextDay,
                    fill = colors.surface,
                    border = colors.action,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = next },
                ) { PixelIcon(R.drawable.ic_px_chevron, colors.action, size = 24.dp) }
            } else {
                // Today is the latest day: the button stays in place, flat and faint, and says it is off.
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(SteppedShape)
                        .steppedBorder(colors.surface2)
                        .semantics {
                            contentDescription = next
                            disabled()
                        },
                    contentAlignment = Alignment.Center,
                ) { PixelIcon(R.drawable.ic_px_chevron, colors.faint, size = 24.dp) }
            }
        }
    }
}

/** KG: − [typed value] +. */
@Composable
private fun KgRow(sheet: WeightSheet, actions: BodyWeightActions) {
    val colors = DinoTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.bw_kg_label), style = DinoTheme.type.label, color = colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val less = stringResource(R.string.bw_less_cd)
            RaisedBox(
                onClick = { actions.onNudge(-1) },
                fill = colors.surface,
                border = colors.outline,
                modifier = Modifier.size(64.dp).semantics { contentDescription = less },
            ) { PixelIcon(R.drawable.ic_px_minus, colors.outline) }
            KgField(sheet.text, sheet.showError, actions.onKgTextChange, actions.onSave)
            val more = stringResource(R.string.bw_more_cd)
            RaisedBox(
                onClick = { actions.onNudge(1) },
                fill = colors.surface,
                border = colors.outline,
                modifier = Modifier.size(64.dp).semantics { contentDescription = more },
            ) { PixelIcon(R.drawable.ic_px_plus, colors.outline) }
        }
    }
}

/**
 * The typed kilograms, big (Pixelify 44sp) in a 132×72dp `bg` box with a decimal keyboard.
 * Like PixelTextField it keeps its own copy of the text, and takes [text] again only when it
 * changes from outside (− and +), so typing never waits for the ViewModel.
 */
@Composable
private fun KgField(text: String, isError: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = DinoTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var lastText by remember { mutableStateOf(text) }
    if (text != lastText) {
        lastText = text
        if (text != field.text) field = TextFieldValue(text, TextRange(text.length))
    }
    val description = stringResource(R.string.bw_kg_field_cd)
    val error = if (isError) stringResource(R.string.bw_kg_error) else null
    Box(
        Modifier
            .width(132.dp)
            .height(72.dp)
            .clip(SteppedShape)
            .background(colors.bg)
            .steppedBorder(if (isError) colors.danger else colors.outline),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = field,
            onValueChange = { next ->
                // Digits and one decimal mark only, at most 5 characters (`123.4`).
                val cleaned = next.text.filter { it.isDigit() || it == '.' || it == ',' }.take(5)
                val changed = cleaned != field.text
                field = next.copy(text = cleaned, selection = if (cleaned == next.text) next.selection else TextRange(cleaned.length))
                if (changed) onChange(cleaned)
            },
            singleLine = true,
            textStyle = DinoTheme.type.numberXl.copy(fontSize = 44.sp, lineHeight = 48.sp, color = colors.outline, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(colors.outline),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = description
                    if (error != null) this.error(error)
                },
        )
    }
}
