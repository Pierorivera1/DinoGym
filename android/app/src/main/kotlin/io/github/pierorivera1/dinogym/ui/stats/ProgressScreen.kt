package io.github.pierorivera1.dinogym.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.rules.ProgressRecord
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import java.time.Instant
import java.util.Locale

/**
 * The progress view of one exercise (mock `Progress.dc.html`): exercise tile and name;
 * BEST / GAIN / RECORDS; the stepped chart of set-1 records; the RECORDS list newest first
 * (`▲ +n`, the first one `FIRST`); and the rule. An exercise never logged shows the mascot
 * instead. No bottom bar. Stateless.
 */
@Composable
fun ProgressScreen(state: ProgressUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(title = stringResource(R.string.progress_title), onBack = onBack)
        when {
            state.isLoading -> return@Column
            state.isMissing -> {
                EmptyMessage(stringResource(R.string.progress_missing))
                return@Column
            }
        }
        val progress = state.progress
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseTile(state.icon, size = 64.dp, iconSize = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    state.name,
                    style = DinoTheme.type.heading.copy(fontSize = 22.sp, lineHeight = 26.sp),
                    color = colors.outline,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (progress.usesWeight) R.string.progress_sub_kg else R.string.progress_sub_reps),
                    style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = colors.muted,
                )
            }
        }
        val best = progress.best
        if (best == null) {
            EmptyMessage(stringResource(R.string.progress_empty, state.name))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPanel(stringResource(R.string.progress_best), StatsFormat.setOne(best, progress.usesWeight, locale), colors.outline, Modifier.weight(1f))
            StatPanel(
                stringResource(R.string.progress_gain),
                StatsFormat.gain(progress.gain, locale),
                if (StatsFormat.isPositive(progress.gain)) colors.success else colors.outline,
                Modifier.weight(1f),
            )
            StatPanel(stringResource(R.string.progress_records), progress.records.size.toString(), colors.outline, Modifier.weight(1f))
        }
        ChartCard(state, locale)
        RecordsList(state, locale)
    }
}

/** A raised panel with a label over a big number, as on the summary. Long values shrink to fit. */
@Composable
private fun StatPanel(label: String, value: String, valueColor: Color, modifier: Modifier) {
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
        BasicText(
            value,
            style = DinoTheme.type.title.copy(fontSize = 28.sp, lineHeight = 28.sp, color = valueColor),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 28.sp, stepSize = 1.sp),
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
private fun ChartCard(state: ProgressUiState, locale: Locale) {
    val colors = DinoTheme.colors
    val progress = state.progress
    // The raw pattern, filled per point: stringResource cannot be called inside the lambda.
    val pointPattern = stringResource(R.string.progress_chart_point)
    val points = progress.records.joinToString(", ") { record ->
        String.format(
            locale,
            pointPattern,
            StatsFormat.setOne(record, progress.usesWeight, locale),
            StatsFormat.spokenDate(Instant.ofEpochMilli(record.date).atZone(state.zone).toLocalDate(), locale),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.progress_chart_label),
            style = DinoTheme.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = colors.muted,
        )
        ProgressChart(
            records = progress.records,
            usesWeight = progress.usesWeight,
            today = state.today,
            zone = state.zone,
            description = stringResource(R.string.progress_chart_cd, state.name, points),
        )
    }
}

@Composable
private fun RecordsList(state: ProgressUiState, locale: Locale) {
    val colors = DinoTheme.colors
    val records = state.progress.recordsNewestFirst
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labelStyle = DinoTheme.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp)
            Text(stringResource(R.string.progress_records), style = labelStyle, color = colors.muted)
            Text(stringResource(R.string.progress_newest), style = labelStyle, color = colors.muted)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SteppedShape)
                .background(colors.surface)
                .steppedBorder(colors.outline)
                .padding(ArtPixel),
        ) {
            records.forEachIndexed { index, record ->
                RecordRow(record, state, locale, newest = index == 0, last = index == records.lastIndex)
            }
        }
        Text(
            stringResource(R.string.progress_rule),
            style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.muted,
        )
    }
}

@Composable
private fun RecordRow(record: ProgressRecord, state: ProgressUiState, locale: Locale, newest: Boolean, last: Boolean) {
    val colors = DinoTheme.colors
    val date = Instant.ofEpochMilli(record.result.date).atZone(state.zone).toLocalDate()
    val gain = record.gain
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (newest) colors.bestRow else Color.Transparent)
            .then(if (!last) Modifier.dottedBottomLine(colors.surface2) else Modifier)
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            StatsFormat.listDate(date, state.today, locale),
            style = DinoTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = colors.outline,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            StatsFormat.setOne(record.result, state.progress.usesWeight, locale),
            style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp),
            color = colors.outline,
            maxLines = 1,
        )
        Text(
            if (gain == null) stringResource(R.string.progress_first) else "▲ ${StatsFormat.gain(gain, locale)}",
            style = DinoTheme.type.button.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = if (gain == null) colors.muted else colors.success,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(80.dp),
        )
    }
}
