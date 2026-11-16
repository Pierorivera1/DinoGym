package io.github.pierorivera1.dinogym.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * A one-line text field (DESIGN.md "Text field"): [label] above (with an optional
 * [labelEnd] on the right, such as "Changes save as you go"), then a 52dp stepped box with a
 * `bg` fill, 2dp `outline` border and `body` 16sp text. [error] shows under the box in
 * `danger` and is announced as the field's error.
 *
 * There is no Save button anywhere (decision 41): every edit goes straight to
 * [onValueChange], and the ViewModel stores it (debounced). The field keeps its own copy of
 * the text, so typing never waits for the ViewModel; when [value] changes from outside (for
 * example loaded from the database), that new value replaces the text.
 *
 * Optional: a 24dp [leadingIcon] in `muted` (the search field), a [trailing] slot (a clear
 * button), a [focusRequester] so a screen can focus it, and [selectAllInitially] so a default
 * name such as "New exercise" is replaced by the first key press. Without a [label],
 * pass [contentDescription] so screen readers can name the field.
 */
@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelEnd: String? = null,
    error: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    selectAllInitially: Boolean = false,
    maxLength: Int = 80,
) {
    val colors = DinoTheme.colors
    var field by remember {
        mutableStateOf(TextFieldValue(value, if (selectAllInitially) TextRange(0, value.length) else TextRange(value.length)))
    }
    // Only a change of [value] itself counts as "from outside". While the ViewModel catches up,
    // [value] still holds the previous text and has not changed, so nothing is reset; when the
    // echo of the typed text arrives it equals the field's text, so the cursor does not move.
    var lastValue by remember { mutableStateOf(value) }
    if (value != lastValue) {
        lastValue = value
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = DinoTheme.type.label.copy(fontSize = 13.sp), color = colors.muted)
                if (labelEnd != null) {
                    Text(labelEnd, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
                }
            }
        }
        val description = contentDescription ?: label
        BasicTextField(
            value = field,
            onValueChange = { next ->
                val limited = if (next.text.length > maxLength) next.copy(text = next.text.take(maxLength)) else next
                val changed = limited.text != field.text
                field = limited
                // Moving the cursor is not an edit.
                if (changed) onValueChange(limited.text)
            },
            singleLine = true,
            textStyle = DinoTheme.type.body.copy(color = colors.outline),
            cursorBrush = SolidColor(colors.outline),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics {
                    if (description != null) this.contentDescription = description
                    if (error != null) this.error(error)
                },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(SteppedShape)
                        .background(colors.bg)
                        .steppedBorder(if (error != null) colors.danger else colors.outline)
                        .padding(start = if (leadingIcon != null) 12.dp else 14.dp, end = if (trailing != null) 2.dp else 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) PixelIcon(leadingIcon, colors.muted, size = 24.dp)
                    Box(Modifier.weight(1f)) { innerTextField() }
                    trailing?.invoke()
                }
            },
        )
        if (error != null) {
            Text(error, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.danger)
        }
    }
}
