package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import kotlinx.coroutines.delay

/** How long the UNDO bar stays, before accessibility settings stretch it. */
const val UNDO_BAR_MILLIS = 5_000L

/**
 * The bar shown after an item is removed from a list (decision 41: removing happens at once,
 * with UNDO instead of a "are you sure?"). A floating `surface` box with an `outline` border
 * and the hard shadow, [message] on the left ("Removed Dips") and a secondary UNDO button.
 *
 * It closes itself: after [UNDO_BAR_MILLIS] (longer when the phone's accessibility settings
 * ask for more time) it calls [onTimeout]; a new [message] restarts the wait. The removal is
 * already saved; the screen keeps what it needs to put the item back until the timeout, and
 * shows the bar while it has one, for example at the bottom of a full-screen Box. Screen
 * readers announce the message when it appears.
 */
@Composable
fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    undoLabel: String = stringResource(R.string.action_undo),
) {
    val colors = DinoTheme.colors
    val timeout by rememberUpdatedState(onTimeout)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message) {
        val wait = accessibility?.calculateRecommendedTimeoutMillis(
            UNDO_BAR_MILLIS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: UNDO_BAR_MILLIS
        delay(wait)
        timeout()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .hardShadow(colors.shadow, SteppedShape)
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = DinoTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = colors.outline,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(undoLabel, onUndo, Modifier.heightIn(max = 48.dp))
    }
}
