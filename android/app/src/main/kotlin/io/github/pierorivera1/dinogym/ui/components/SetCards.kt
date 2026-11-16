package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * A set that is done (DESIGN.md "Set row"): `surface`, `success` border, check icon,
 * [label] (`SET 1`), the [value] in Pixelify with its [unit] (`7 reps`), and the
 * `▲ NEW BEST` tag when [newBestTag] is not null. Tapping it calls [onClick]
 * (the workout uses that to correct or delete the set); [onClickLabel] is what a screen
 * reader announces for the tap.
 */
@Composable
fun SetRow(
    label: String,
    value: String,
    unit: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    newBestTag: String? = null,
) {
    val colors = DinoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.success)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon(R.drawable.ic_px_check, colors.success)
        Text(label, style = DinoTheme.type.label, color = colors.muted)
        Text(
            text = buildAnnotatedString {
                append(value)
                append(" ")
                withStyle(DinoTheme.type.body.toSpanStyle().copy(fontSize = 14.sp, color = colors.muted)) { append(unit) }
            },
            style = DinoTheme.type.title.copy(fontSize = 26.sp, lineHeight = 30.sp),
            color = colors.outline,
            modifier = Modifier.weight(1f),
        )
        if (newBestTag != null) {
            Text(
                text = newBestTag,
                style = DinoTheme.type.button.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = colors.onSelect,
                modifier = Modifier
                    .background(colors.select)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The set being logged (DESIGN.md "Active set card"): selected-state colours, stepped,
 * hard shadow, the cursor before [title] (`SET 2 OF 2`), the [content] (steppers), and a
 * muted [footer] such as "Pre-filled from last session".
 */
@Composable
fun ActiveSetCard(
    title: String,
    footer: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DinoTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(colors.shadow, SteppedShape)
            .clip(SteppedShape)
            .background(colors.selBg)
            .steppedBorder(colors.selBorder)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cursor(scale = 3)
            Text(title, style = DinoTheme.type.button, color = colors.selText)
        }
        content()
        if (footer != null) {
            Text(footer, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
        }
    }
}
