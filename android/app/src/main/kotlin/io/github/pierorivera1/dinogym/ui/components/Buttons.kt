package io.github.pierorivera1.dinogym.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * The shared body of every pressable pixel component: stepped corners, a [fill], a 2dp
 * [border], the raised bands, and the pressed look (down 2dp, bottom band gone, no ripple).
 * Buttons, stepper buttons and bottom bar tabs are all built on it, so they press the same way.
 * [role] lets a bottom bar tab say it is a tab rather than a button.
 */
@Composable
internal fun RaisedBox(
    onClick: () -> Unit,
    fill: Color,
    border: Color,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .offset { IntOffset(0, if (pressed) 2.dp.roundToPx() else 0) }
            .clip(SteppedShape)
            .background(fill)
            .raised(DinoTheme.colors, pressed)
            .steppedBorder(border)
            .clickable(interactionSource = interaction, indication = null, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The main action on a screen (LOG SET, START PULL): `primary` fill, `outline` border,
 * `button` text, and an optional 16x16 [icon] at 2x on the left. 56dp tall by default;
 * pass `Modifier.height(64.dp)` for the larger ones.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val colors = DinoTheme.colors
    RaisedBox(onClick, colors.primary, colors.outline, modifier.height(56.dp)) {
        ButtonContent(text, icon, textColor = colors.onPrimary, iconColor = colors.onPrimary)
    }
}

/**
 * A secondary action (SKIP, NOTE, the rest time): `surface` fill, `action` border.
 * The optional [icon] is drawn in the `action` colour, as in the mocks.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val colors = DinoTheme.colors
    RaisedBox(onClick, colors.surface, colors.action, modifier.height(56.dp)) {
        ButtonContent(text, icon, textColor = colors.outline, iconColor = colors.action)
    }
}

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, textColor: Color, iconColor: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) PixelIcon(icon, iconColor)
        Text(text, style = DinoTheme.type.button, color = textColor, maxLines = 1)
    }
}

/**
 * A 48dp square button with a transparent fill and a `danger` border and icon: the ✕ that
 * ends a workout. Not raised, so it does not invite a casual tap. [contentDescription] is
 * required because the button has no visible text.
 */
@Composable
fun DangerIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val danger = DinoTheme.colors.danger
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(SteppedShape)
            .steppedBorder(danger)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(R.drawable.ic_px_close, danger, contentDescription = contentDescription)
    }
}
