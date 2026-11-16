package io.github.pierorivera1.dinogym.ui.components

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import kotlinx.coroutines.delay

/** About 40 characters a second, like an RPG dialogue box. */
private const val TYPEWRITER_DELAY_MS = 25L

/**
 * The RPG-style dialogue box: `bg` fill, 3dp `outline` border, hard shadow, and [text]
 * after a `* ` prefix (the component adds it, so strings do not repeat it). With
 * [showMascot], Dinocito sits on the left.
 *
 * The text types itself out; a tap shows it all at once. When the phone's animations are
 * turned off (Settings > Accessibility > Remove animations), the text appears at once.
 * The untyped part is drawn transparent instead of left out, so the box never changes size
 * while typing and screen readers always get the whole sentence.
 */
@Composable
fun TextBox(
    text: String,
    modifier: Modifier = Modifier,
    showMascot: Boolean = false,
) {
    val colors = DinoTheme.colors
    val line = "* $text"
    // areAnimatorsEnabled() is false when the system animator duration scale is 0.
    var shown by remember(line) {
        mutableIntStateOf(if (ValueAnimator.areAnimatorsEnabled()) 0 else line.length)
    }
    LaunchedEffect(line) {
        while (shown < line.length) {
            delay(TYPEWRITER_DELAY_MS)
            shown++
        }
    }
    val typing = shown < line.length
    val styled = buildAnnotatedString {
        append(line.take(shown))
        withStyle(SpanStyle(color = Color.Transparent)) { append(line.drop(shown)) }
    }

    Row(
        modifier = modifier
            .hardShadow(colors.shadow)
            .background(colors.bg)
            .border(3.dp, colors.outline)
            .then(
                if (typing) {
                    Modifier.clickable(interactionSource = null, indication = null) { shown = line.length }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMascot) Mascot()
        Text(styled, style = DinoTheme.type.body, color = colors.outline)
    }
}
