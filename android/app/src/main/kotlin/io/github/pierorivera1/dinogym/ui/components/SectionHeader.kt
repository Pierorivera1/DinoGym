package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * The `+ NEW` button (DESIGN.md "Section header"): 48dp tall, secondary style, a 24dp plus
 * in `action` and [text] in Pixelify 16sp. [contentDescription] names what it creates
 * ("New exercise"), because "NEW" alone is not enough for a screen reader.
 */
@Composable
fun NewButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.action_new),
) {
    val colors = DinoTheme.colors
    RaisedBox(
        onClick = onClick,
        fill = colors.surface,
        border = colors.action,
        modifier = modifier.height(48.dp).semantics { this.contentDescription = contentDescription },
    ) {
        Row(
            // The button's description replaces the visible "NEW", so it is not read twice.
            modifier = Modifier.padding(start = 8.dp, end = 14.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(R.drawable.ic_px_plus, colors.action, size = 24.dp)
            Text(text, style = DinoTheme.type.button.copy(fontSize = 16.sp, lineHeight = 20.sp), color = colors.outline)
        }
    }
}

/**
 * The header of a list (DESIGN.md "Section header"): [title] on the left and, when [onNew]
 * is set, the `+ NEW` button on the right. Adding is always done here (decision 41).
 *
 * By default the title is a `label` (YOUR PROGRAMS, ROUTINES) in a 48dp row. With
 * [isScreenTitle] it is the screen's own title (EXERCISES, Pixelify 26sp) in a 56dp row, as
 * on the exercise library. Either way it is marked as a heading for screen readers.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    isScreenTitle: Boolean = false,
    onNew: (() -> Unit)? = null,
    newContentDescription: String = "",
    newText: String = stringResource(R.string.action_new),
) {
    val colors = DinoTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().height(if (isScreenTitle) 56.dp else 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = if (isScreenTitle) {
                DinoTheme.type.title.copy(fontSize = 26.sp, lineHeight = 30.sp)
            } else {
                DinoTheme.type.label.copy(fontSize = 13.sp)
            },
            color = if (isScreenTitle) colors.outline else colors.muted,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onNew != null) NewButton(onNew, newContentDescription, text = newText)
    }
}
