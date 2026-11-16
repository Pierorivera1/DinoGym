package io.github.pierorivera1.dinogym.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.Mascot
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.home.shortDate
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * The workout summary (mock `Summary.dc.html`): title, routine, duration and date; the text
 * box naming any new best; TIME / SETS / BESTS; set-1 reps per exercise against last time;
 * the next step in the cycle; EDIT and FINISH. Stateless.
 */
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isLoading) return@Column
        val duration = WorkoutFormat.clock(state.durationSeconds)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Mascot(size = 80.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.summary_title),
                        style = DinoTheme.type.title.copy(fontSize = 30.sp, lineHeight = 32.sp),
                        color = colors.outline,
                        modifier = Modifier.semantics { heading() },
                    )
                    val sub = listOfNotNull(state.routineName, duration, state.date?.let { shortDate(it, locale) })
                    Text(sub.joinToString(" · "), style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
                }
            }
            TextBox(bubble(state), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPanel(stringResource(R.string.label_time), duration, Modifier.weight(1f))
                StatPanel(stringResource(R.string.label_sets), "${state.setCount}/${state.plannedSets}", Modifier.weight(1f))
                StatPanel(stringResource(R.string.label_bests), state.bests.size.toString(), Modifier.weight(1f))
            }
            if (state.rows.isNotEmpty()) SetOneList(state.rows)
        }
        if (state.hasNextStep) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.summary_next), style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
                Text(
                    state.nextRoutine?.uppercase() ?: stringResource(R.string.summary_next_rest),
                    style = DinoTheme.type.button.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = colors.outline,
                    modifier = Modifier.border(ArtPixel, colors.outline).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.action_edit), onEdit, Modifier.weight(1f).height(60.dp))
            PrimaryButton(stringResource(R.string.action_finish), onFinish, Modifier.weight(2f).height(60.dp), icon = R.drawable.ic_px_check)
        }
    }
}

@Composable
private fun bubble(state: SummaryUiState): String {
    val locale = LocalConfiguration.current.locales[0]
    val best = state.bests.singleOrNull()
    return when {
        best != null -> stringResource(
            R.string.summary_bubble_one,
            best.exerciseName.lowercase(locale),
            pluralStringResource(R.plurals.workout_reps, best.reps, best.reps.toString()),
        )
        state.bests.size > 1 -> pluralStringResource(R.plurals.summary_bubble_many, state.bests.size, state.bests.size)
        else -> stringResource(R.string.summary_bubble_none)
    }
}

/** One of TIME / SETS / BESTS: a raised panel with a label over a big number. */
@Composable
private fun StatPanel(label: String, value: String, modifier: Modifier) {
    val colors = DinoTheme.colors
    Column(
        modifier
            .clip(SteppedShape)
            .background(colors.surface)
            .raised(colors, pressed = false)
            .steppedBorder(colors.outline)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = DinoTheme.type.label.copy(fontSize = 12.sp, lineHeight = 15.sp), color = colors.muted, maxLines = 1)
        Text(value, style = DinoTheme.type.title.copy(fontSize = 28.sp, lineHeight = 28.sp), color = colors.outline, maxLines = 1)
    }
}

@Composable
private fun SetOneList(rows: List<SummaryRow>) {
    val colors = DinoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labelStyle = DinoTheme.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp)
            Text(stringResource(R.string.summary_list), style = labelStyle, color = colors.muted)
            Text(stringResource(R.string.summary_vs_last), style = labelStyle, color = colors.muted)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SteppedShape)
                .background(colors.surface)
                .steppedBorder(colors.outline)
                // Keep the rows inside the border, which is drawn behind them.
                .padding(ArtPixel),
        ) {
            rows.forEachIndexed { index, row ->
                val improved = (row.delta ?: 0) > 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(if (improved) colors.bestRow else Color.Transparent)
                        .then(if (index < rows.lastIndex) Modifier.dottedBottomLine(colors.surface2) else Modifier)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.exerciseName,
                        style = DinoTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        color = colors.outline,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.reps?.toString() ?: "–",
                        style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp),
                        color = colors.outline,
                    )
                    Text(
                        deltaText(row.delta),
                        style = DinoTheme.type.button.copy(fontSize = 15.sp, lineHeight = 18.sp),
                        color = if (improved) colors.success else colors.muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
            }
        }
    }
}

/** `=`, `▲ +1`, `−2`, or `–` with nothing to compare. */
internal fun deltaText(delta: Int?): String = when {
    delta == null -> "–"
    delta > 0 -> "▲ +$delta"
    delta < 0 -> "−${-delta}"
    else -> "="
}
