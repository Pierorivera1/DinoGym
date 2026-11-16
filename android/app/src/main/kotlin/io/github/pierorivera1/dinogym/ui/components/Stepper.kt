package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * A number with big − and + buttons (64dp, easy to hit between sets), and a [label] on the
 * left (`REPS`, `KG`). The caller owns [value] and changes it in [onDecrease] / [onIncrease].
 * [decreaseDescription] and [increaseDescription] are what a screen reader says for the buttons.
 * [valueText] is what is shown for [value]; the weight stepper passes kilograms such as `22.5`.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    valueText: String = value.toString(),
) {
    val colors = DinoTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DinoTheme.type.label, color = colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RaisedBox(
                onClick = onDecrease,
                fill = colors.surface,
                border = colors.outline,
                modifier = Modifier.size(64.dp).semantics { contentDescription = decreaseDescription },
            ) {
                PixelIcon(R.drawable.ic_px_minus, colors.outline)
            }
            Text(
                text = valueText,
                style = DinoTheme.type.numberXl,
                color = colors.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            RaisedBox(
                onClick = onIncrease,
                fill = colors.surface,
                border = colors.outline,
                modifier = Modifier.size(64.dp).semantics { contentDescription = increaseDescription },
            ) {
                PixelIcon(R.drawable.ic_px_plus, colors.outline)
            }
        }
    }
}
