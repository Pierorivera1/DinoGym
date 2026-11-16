package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * A progress row made of blocks, like an RPG stat bar: [label], [total] outlined blocks
 * of which the first [filled] are `primary`, then [value] (`3/6`, `0:45`) in Pixelify.
 * With [markCurrent], the block after the filled ones is `select`: the step in progress.
 * The blocks share the width left over by the label and value.
 */
@Composable
fun StatBar(
    label: String,
    total: Int,
    filled: Int,
    value: String,
    modifier: Modifier = Modifier,
    markCurrent: Boolean = false,
) {
    val colors = DinoTheme.colors
    Row(
        // Screen readers hear "EXERCISE 3/6" instead of a list of blocks.
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label $value" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DinoTheme.type.label.copy(fontSize = 13.sp), color = colors.muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                val fill = when {
                    i < filled -> colors.primary
                    markCurrent && i == filled -> colors.select
                    else -> Color.Transparent
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(fill)
                        .border(ArtPixel, colors.outline),
                )
            }
        }
        Text(value, style = DinoTheme.type.button, color = colors.outline)
    }
}
