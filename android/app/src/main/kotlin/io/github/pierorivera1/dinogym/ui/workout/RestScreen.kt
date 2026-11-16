package io.github.pierorivera1.dinogym.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.timer.RestCountdown
import io.github.pierorivera1.dinogym.ui.components.Mascot
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/** Every tap the rest timer screen reports. */
data class RestActions(
    val onEnd: () -> Unit,
    val onMinus: () -> Unit,
    val onPlus: () -> Unit,
    val onSkip: () -> Unit,
    val onSaveAndFinish: () -> Unit,
    val onDiscard: () -> Unit,
    val onCloseEnd: () -> Unit,
) {
    companion object {
        /** Does nothing: for previews and screenshot tests. */
        val None = RestActions({}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * The rest timer (mock `Rest.dc.html`): the big countdown, a 10-block bar, −15s / +15s,
 * Dinocito saying what comes next, SKIP REST, and a note that it keeps running with the
 * screen off. Stateless: it draws [state] and reports taps through [actions].
 */
@Composable
fun RestScreen(state: RestUiState, actions: RestActions, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WorkoutTopBar(state.routineName, state.elapsedSeconds, actions.onEnd)
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.label_rest),
                    style = DinoTheme.type.title.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.04.em),
                    color = colors.muted,
                )
                val left = stringResource(R.string.cd_rest_left, spokenDuration(state.remainingSeconds))
                Text(
                    WorkoutFormat.countdown(state.remainingSeconds),
                    style = DinoTheme.type.numberXl.copy(fontSize = 104.sp, lineHeight = 104.sp),
                    color = colors.outline,
                    // Read on focus only; announcing every second would talk over the user.
                    modifier = Modifier.clearAndSetSemantics { contentDescription = left },
                )
                BlockBar(state.filledBlocks)
                val total = WorkoutFormat.clock(state.totalSeconds.toLong())
                Text(
                    if (state.doneSetIndex != null && state.doneExerciseName != null) {
                        stringResource(R.string.rest_of_done, total, state.doneSetIndex, state.doneExerciseName.lowercase(locale))
                    } else {
                        stringResource(R.string.rest_of, total)
                    },
                    style = DinoTheme.type.body.copy(fontSize = 14.sp),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val less = stringResource(R.string.cd_rest_minus)
                val more = stringResource(R.string.cd_rest_plus)
                SecondaryButton(
                    stringResource(R.string.rest_minus),
                    actions.onMinus,
                    Modifier.weight(1f).semantics { contentDescription = less },
                )
                SecondaryButton(
                    stringResource(R.string.rest_plus),
                    actions.onPlus,
                    Modifier.weight(1f).semantics { contentDescription = more },
                )
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    Mascot(size = 96.dp)
                    TextBox(
                        if (state.nextLabel.isBlank()) {
                            stringResource(R.string.rest_bubble_plain)
                        } else {
                            stringResource(R.string.rest_bubble, state.nextLabel.lowercase(locale))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    stringResource(R.string.action_skip_rest),
                    actions.onSkip,
                    Modifier.fillMaxWidth().height(64.dp),
                    icon = R.drawable.ic_px_skip,
                )
                Text(
                    stringResource(R.string.rest_background),
                    style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.showEndSheet) {
            EndWorkoutSheet(state.loggedSetCount, actions.onSaveAndFinish, actions.onDiscard, actions.onCloseEnd)
        }
    }
}

/** The 10 blocks under the countdown: the filled ones are the share of the rest still to go. */
@Composable
private fun BlockBar(filled: Int) {
    val colors = DinoTheme.colors
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(RestCountdown.BLOCKS) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(20.dp)
                    .background(if (i < filled) colors.primary else Color.Transparent)
                    .border(ArtPixel, colors.outline),
            )
        }
    }
}
