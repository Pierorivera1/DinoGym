package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

// On/off controls: the switch and the checkbox. Both are drawn by us, because M3's
// rounded ones do not fit the pixel style.

/**
 * The switch's look (DESIGN.md "Switch"): a 60×32 stepped box with a 2dp `outline` border,
 * 4dp padding and a 20dp square knob. Off: `surface2` fill, `outline` knob on the left.
 * On: `primary` fill, `onPrimary` knob on the right. It is drawn centred in a 64×48 box,
 * the touch target. Decorative: the row around it is the toggle (see [SwitchRow]).
 */
@Composable
fun PixelSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    Box(modifier.size(width = 64.dp, height = 48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 60.dp, height = 32.dp)
                .clip(SteppedShape)
                .background(if (checked) colors.primary else colors.surface2)
                .steppedBorder(colors.outline)
                .padding(ArtPixel + 4.dp), // 4dp inside the 2dp border
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).background(if (checked) colors.onPrimary else colors.outline))
        }
    }
}

/**
 * A setting row with a switch, as "Log weight" in the exercise editor: a `surface` box with
 * a 2dp `surface2` border, [title] (Inter SemiBold 15sp), an optional [help] line in `muted`,
 * and the [PixelSwitch] on the right. The whole row is one toggle for touch and screen
 * readers, so the 64×48 switch is never the only target.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
) {
    val colors = DinoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.surface2)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DinoTheme.type.body.copy(fontSize = 15.sp, fontWeight = DinoTheme.type.label.fontWeight), color = colors.outline)
            if (help != null) {
                Text(help, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
            }
        }
        PixelSwitch(checked)
    }
}

/**
 * The checkbox's look (DESIGN.md "Checkbox"): a 28dp square with a 2dp `outline` border;
 * checked, it has a `select` fill and the check icon in `onSelect`. Decorative: put
 * `Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = …)` on the
 * whole row, so the row is the target, as in the exercise picker.
 */
@Composable
fun PixelCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    Box(
        modifier
            .size(28.dp)
            .background(if (checked) colors.select else colors.bg)
            // A plain square, as in the mock: the only box without stepped corners.
            .border(ArtPixel, colors.outline),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) PixelIcon(R.drawable.ic_px_check, colors.onSelect, size = 24.dp)
    }
}
