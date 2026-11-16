package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/** How a [Tag] is drawn. */
enum class TagStyle {
    /** `select` fill with `onSelect` text: NEXT, ▲ NEW BEST. */
    Filled,

    /** A 2dp border and text in one colour, no fill: ACTIVE on a program card, IN ROUTINE. */
    Outlined,
}

/**
 * A small label next to a name, in Pixelify 12sp (`NEXT`, `ACTIVE`, `IN ROUTINE`).
 * [color] is only used by [TagStyle.Outlined]; pass the colour of the text around it
 * (`selText` on a selected card, `muted` in a list). Text, so screen readers read it.
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    style: TagStyle = TagStyle.Filled,
    color: Color = DinoTheme.colors.muted,
) {
    val colors = DinoTheme.colors
    val textStyle = DinoTheme.type.button.copy(fontSize = 12.sp, lineHeight = 14.sp)
    when (style) {
        TagStyle.Filled -> Text(
            text = text,
            style = textStyle,
            color = colors.onSelect,
            maxLines = 1,
            modifier = modifier.background(colors.select).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        TagStyle.Outlined -> Text(
            text = text,
            style = textStyle,
            color = color,
            maxLines = 1,
            modifier = modifier.border(ArtPixel, color).padding(horizontal = 6.dp + ArtPixel, vertical = 2.dp + ArtPixel),
        )
    }
}
