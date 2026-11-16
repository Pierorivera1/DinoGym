package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * The top bar of a pushed screen (DESIGN.md "Top bar"): a 56dp row of three cells,
 * 48dp / the rest / 48dp. Left: the back button. Middle: [title] in Pixelify 22sp, centred.
 * Right: [action] (usually a [DangerIconButton] with the trash) or an empty cell, so the
 * title stays centred. Editors and detail screens use it; they have no bottom bar.
 * [backDescription] lets a screen say where back goes ("Back to exercises").
 */
@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backDescription: String = stringResource(R.string.cd_back),
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryIconButton(R.drawable.ic_px_back, backDescription, onBack)
        Text(
            text = title,
            style = DinoTheme.type.title.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = DinoTheme.colors.outline,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { action?.invoke() }
    }
}
