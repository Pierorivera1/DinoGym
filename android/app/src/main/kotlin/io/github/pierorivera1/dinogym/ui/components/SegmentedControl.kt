package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * 2–4 equal boxes with 4dp gaps, 48dp tall (DESIGN.md "Segmented control"). Unselected
 * boxes look like secondary buttons; the selected one has a `select` fill, `onSelect` text,
 * an `outline` border and no bands, so it looks pressed in.
 *
 * Two uses: as tabs that switch what a screen shows ([asTabs], for example ACTIVE /
 * ARCHIVED), or as a radio group that picks a value (TYPE, HOW IT IS LOGGED, THEME).
 * The difference is only what a screen reader calls it. Labels use Pixelify 16sp; with
 * three or more options 14sp, as in the mocks, so SIN REGISTRO fits.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    asTabs: Boolean = false,
) {
    require(options.size in 2..4) { "A segmented control has 2 to 4 options" }
    val colors = DinoTheme.colors
    val textStyle = DinoTheme.type.button.copy(fontSize = if (options.size >= 3) 14.sp else 16.sp, lineHeight = 18.sp)
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val role = if (asTabs) Role.Tab else Role.RadioButton
            val cell = Modifier.weight(1f).height(48.dp)
            if (selected) {
                // Pressed look: flat, no bands. Still selectable, so it reads as "selected".
                Box(
                    cell
                        .clip(SteppedShape)
                        .background(colors.select)
                        .steppedBorder(colors.outline)
                        .selectable(selected = true, interactionSource = null, indication = null, role = role, onClick = {}),
                    contentAlignment = Alignment.Center,
                ) { SegmentLabel(label, textStyle.copy(color = colors.onSelect)) }
            } else {
                RaisedBox(
                    onClick = { onSelect(index) },
                    fill = colors.surface,
                    border = colors.action,
                    role = role,
                    modifier = cell.semantics { this.selected = false },
                ) { SegmentLabel(label, textStyle.copy(color = colors.outline)) }
            }
        }
    }
}

@Composable
private fun SegmentLabel(text: String, style: TextStyle) {
    Text(text, style = style, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
}
