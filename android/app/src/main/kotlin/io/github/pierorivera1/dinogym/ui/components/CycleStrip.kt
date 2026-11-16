package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * One box of the [CycleStrip]. [label] is the routine name in caps, or `ZZZ` for a rest day
 * ([isRest]); [description] is what a screen reader says ("Rest day" instead of "ZZZ").
 */
data class CycleStripItem(
    val label: String,
    val description: String,
    val isRest: Boolean,
    val isCurrent: Boolean,
)

/**
 * The program cycle as a row of equal stepped boxes (DESIGN.md "Cycle strip"). The current
 * step uses the selected-state colours and shows the cursor; rest days show `ZZZ` in `muted`.
 * Long names shrink to fit (12sp down to 8sp) instead of wrapping.
 */
@Composable
fun CycleStrip(items: List<CycleStripItem>, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            val text = when {
                item.isCurrent -> colors.selText
                item.isRest -> colors.muted
                else -> colors.outline
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(SteppedShape)
                    .background(if (item.isCurrent) colors.selBg else colors.surface)
                    .steppedBorder(if (item.isCurrent) colors.selBorder else colors.surface2)
                    .padding(horizontal = 4.dp)
                    .clearAndSetSemantics {
                        contentDescription = item.description
                        selected = item.isCurrent
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                if (item.isCurrent) Cursor(scale = 2)
                BasicText(
                    text = item.label,
                    style = DinoTheme.type.button.copy(fontSize = 12.sp, lineHeight = 14.sp, color = text),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                )
            }
        }
    }
}
