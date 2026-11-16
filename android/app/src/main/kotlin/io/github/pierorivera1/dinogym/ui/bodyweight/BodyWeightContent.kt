package io.github.pierorivera1.dinogym.ui.bodyweight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.rules.GoalDirection
import io.github.pierorivera1.dinogym.domain.rules.WeightRange
import io.github.pierorivera1.dinogym.ui.components.DangerIconButton
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.components.UndoBar
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.home.shortDate
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import java.util.Locale
import kotlin.math.abs

/** Every tap of the WEIGHT segment and its sheets. Each is stored at once (decision 41). */
data class BodyWeightActions(
    val onSelectRange: (WeightRange) -> Unit,
    val onLog: () -> Unit,
    val onOpenGoal: () -> Unit,
    val onDelete: (WeighInRow) -> Unit,
    val onUndo: () -> Unit,
    val onUndoTimeout: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onPreviousDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onKgTextChange: (String) -> Unit,
    val onNudge: (steps: Int) -> Unit,
    val onSave: () -> Unit,
    val onClearGoal: () -> Unit,
) {
    companion object {
        /** No-op actions, for screenshot tests. */
        val None = BodyWeightActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** The ViewModel's functions as [BodyWeightActions], for the Stats tab. */
@Composable
fun rememberBodyWeightActions(viewModel: BodyWeightViewModel): BodyWeightActions =
    remember(viewModel) {
        BodyWeightActions(
            onSelectRange = viewModel::selectRange,
            onLog = viewModel::openLog,
            onOpenGoal = viewModel::openGoal,
            onDelete = viewModel::delete,
            onUndo = viewModel::undoDelete,
            onUndoTimeout = viewModel::dismissUndo,
            onCloseSheet = viewModel::closeSheet,
            onPreviousDay = viewModel::previousDay,
            onNextDay = viewModel::nextDay,
            onKgTextChange = viewModel::onKgTextChange,
            onNudge = viewModel::nudge,
            onSave = viewModel::save,
            onClearGoal = viewModel::clearGoal,
        )
    }

/**
 * The scrolling part: CURRENT / CHANGE / GOAL panels, the 1M / 3M / ALL range, the chart,
 * LOG WEIGHT and the weigh-ins newest first, each with a delete button. With no weigh-ins at
 * all, a mascot text box and LOG WEIGHT. It has no side padding: it sits inside the Stats
 * screen's 16dp, directly under the segmented control.
 */
@Composable
fun BodyWeightContent(state: BodyWeightUiState, actions: BodyWeightActions, modifier: Modifier = Modifier) {
    if (state.isLoading) return
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        // Items carry their own 12dp gap below; the weigh-in rows touch, so the list reads as one framed box.
        val gap = Modifier.padding(bottom = 12.dp)
        if (!state.hasWeighIns) {
            item { TextBox(stringResource(R.string.bw_empty), showMascot = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).then(gap)) }
            item { LogButton(actions.onLog, Modifier.padding(top = 8.dp)) }
            return@LazyColumn
        }
        item { Box(gap) { Panels(state, locale, actions.onOpenGoal) } }
        item {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.bw_range_1m),
                    stringResource(R.string.bw_range_3m),
                    stringResource(R.string.bw_range_all),
                ),
                selectedIndex = state.range.ordinal,
                onSelect = { actions.onSelectRange(WeightRange.entries[it]) },
                modifier = gap,
            )
        }
        item { Box(gap) { ChartFrame(state, locale) } }
        item { LogButton(actions.onLog, gap) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.bw_list_label), style = DinoTheme.type.label.copy(fontSize = 13.sp), color = colors.muted)
                Text(stringResource(R.string.bw_newest_first), style = DinoTheme.type.label.copy(fontSize = 13.sp), color = colors.muted)
            }
        }
        itemsIndexed(state.weighIns, key = { _, row -> row.id }) { index, row ->
            WeighInListRow(row, locale, isFirst = index == 0, isLast = index == state.weighIns.lastIndex, onDelete = { actions.onDelete(row) })
        }
    }
}

/** The open sheet and the UNDO bar, over [BodyWeightContent]. */
@Composable
fun BodyWeightOverlays(state: BodyWeightUiState, actions: BodyWeightActions, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Box(modifier.fillMaxSize()) {
        state.undo?.let { row ->
            UndoBar(
                message = stringResource(R.string.bw_deleted, BodyWeightStateMapper.kgText(row.weightKg, locale), shortDate(row.date, locale)),
                onUndo = actions.onUndo,
                onTimeout = actions.onUndoTimeout,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
        state.sheet?.let { WeightSheetView(it, locale, actions) }
    }
}

@Composable
private fun LogButton(onLog: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(stringResource(R.string.bw_log), onLog, modifier.fillMaxWidth(), icon = R.drawable.ic_px_plus)
}

/** CURRENT, CHANGE and GOAL. GOAL is a button that opens the goal sheet. */
@Composable
private fun Panels(state: BodyWeightUiState, locale: Locale, onOpenGoal: () -> Unit) {
    val colors = DinoTheme.colors
    val none = "–"
    val change = state.changeKg
    val changeText = when {
        change == null -> none
        change < 0 -> "▼ " + BodyWeightStateMapper.kgText(abs(change), locale)
        change > 0 -> "▲ " + BodyWeightStateMapper.kgText(change, locale)
        else -> BodyWeightStateMapper.kgText(0.0, locale)
    }
    val changeSub = when (state.range) {
        WeightRange.ONE_MONTH -> R.string.bw_change_sub_1m
        WeightRange.THREE_MONTHS -> R.string.bw_change_sub_3m
        WeightRange.ALL -> R.string.bw_change_sub_all
    }
    val goal = state.goal
    val goalSub = when {
        state.goalKg == null -> stringResource(R.string.bw_goal_none)
        goal == null -> ""
        goal.direction == GoalDirection.REACHED -> stringResource(R.string.bw_goal_reached)
        else -> stringResource(R.string.bw_goal_to_go, BodyWeightStateMapper.kgText(goal.remainingKg, locale))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val panel = Modifier.weight(1f).height(92.dp)
        Box(panel.clip(SteppedShape).background(colors.surface).raised(colors, pressed = false).steppedBorder(colors.outline)) {
            PanelText(
                stringResource(R.string.bw_current),
                state.currentKg?.let { BodyWeightStateMapper.kgText(it, locale) } ?: none,
                stringResource(R.string.bw_current_sub),
                colors.outline,
            )
        }
        Box(panel.clip(SteppedShape).background(colors.surface).raised(colors, pressed = false).steppedBorder(colors.outline)) {
            PanelText(
                stringResource(R.string.bw_change),
                changeText,
                stringResource(changeSub),
                if (state.changeTowardGoal) colors.success else colors.outline,
            )
        }
        val goalDescription = stringResource(R.string.bw_goal_cd)
        RaisedBox(
            onClick = onOpenGoal,
            fill = colors.surface,
            border = colors.action,
            modifier = panel.semantics { contentDescription = goalDescription },
        ) {
            PanelText(
                stringResource(R.string.bw_goal),
                state.goalKg?.let { BodyWeightStateMapper.kgText(it, locale) } ?: none,
                goalSub,
                colors.outline,
            )
        }
    }
}

@Composable
private fun PanelText(label: String, value: String, sub: String, valueColor: Color) {
    val colors = DinoTheme.colors
    Column(
        Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = DinoTheme.type.label.copy(fontSize = 12.sp, lineHeight = 14.sp), color = colors.muted, maxLines = 1)
        Text(value, style = DinoTheme.type.title.copy(fontSize = 24.sp, lineHeight = 26.sp), color = valueColor, maxLines = 1)
        Text(
            sub,
            style = DinoTheme.type.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The framed chart with its legend, or a short note when the range holds no weigh-in. */
@Composable
private fun ChartFrame(state: BodyWeightUiState, locale: Locale) {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Legend(showGoal = state.goalKg != null)
        val chart = state.chart
        if (chart == null) {
            Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bw_chart_empty), style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.muted)
            }
        } else {
            WeightChartCanvas(chart, chartSummary(state, chart, locale), locale)
        }
    }
}

@Composable
private fun chartSummary(state: BodyWeightUiState, chart: WeightChart, locale: Locale): String {
    val phrase = stringResource(
        when (state.range) {
            WeightRange.ONE_MONTH -> R.string.bw_range_phrase_1m
            WeightRange.THREE_MONTHS -> R.string.bw_range_phrase_3m
            WeightRange.ALL -> R.string.bw_range_phrase_all
        },
    )
    val first = chart.average.firstOrNull()?.kg ?: chart.raw.first().kg
    val last = chart.average.lastOrNull()?.kg ?: chart.raw.last().kg
    val base = stringResource(
        R.string.bw_chart_summary,
        phrase,
        BodyWeightStateMapper.kgText(first, locale),
        BodyWeightStateMapper.kgText(last, locale),
    )
    val goal = state.goalKg?.let { stringResource(R.string.bw_chart_summary_goal, BodyWeightStateMapper.kgText(it, locale)) }
    return base + (goal ?: "")
}

/** A one-line key: the average line, a weigh-in point and, with a goal, its dashes. */
@Composable
private fun Legend(showGoal: Boolean) {
    val colors = DinoTheme.colors
    val style = DinoTheme.type.body.copy(fontSize = 12.sp, lineHeight = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(stringResource(R.string.bw_legend_average), style) { Box(Modifier.size(14.dp, 2.dp).background(colors.primary)) }
        LegendItem(stringResource(R.string.bw_legend_weigh_in), style) { Box(Modifier.size(4.dp).background(colors.muted)) }
        if (showGoal) {
            LegendItem(stringResource(R.string.bw_legend_goal), style) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { Box(Modifier.size(6.dp, 2.dp).background(goalLineColor(colors))) }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(text: String, style: TextStyle, swatch: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Text(text, style = style, color = DinoTheme.colors.muted, maxLines = 1)
    }
}

/** One weigh-in: date, weight, and a delete button (UNDO follows, so no question first). */
@Composable
private fun WeighInListRow(row: WeighInRow, locale: Locale, isFirst: Boolean, isLast: Boolean, onDelete: () -> Unit) {
    val colors = DinoTheme.colors
    val date = shortDate(row.date, locale)
    // The list reads as one framed box: the frame is drawn per row, with the dotted divider between rows.
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surface)
            .listFrame(colors.outline, isFirst, isLast)
            .then(if (isLast) Modifier else Modifier.dottedBottomLine(colors.surface2))
            .padding(start = 14.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.outline, modifier = Modifier.weight(1f))
        Text(
            BodyWeightStateMapper.kgText(row.weightKg, locale) + " kg",
            style = DinoTheme.type.button.copy(fontSize = 20.sp, lineHeight = 24.sp),
            color = colors.outline,
        )
        DangerIconButton(
            contentDescription = stringResource(R.string.bw_delete_cd, date),
            onClick = onDelete,
            icon = R.drawable.ic_px_trash,
            iconSize = 24.dp,
        )
    }
}

/**
 * The 2dp [color] frame of the weigh-in list, drawn per row so the list can stay lazy: the
 * sides on every row, the top on the first and the bottom on the last.
 */
private fun Modifier.listFrame(color: Color, isFirst: Boolean, isLast: Boolean): Modifier = drawBehind {
    val line = 2.dp.toPx()
    drawRect(color, Offset.Zero, Size(line, size.height))
    drawRect(color, Offset(size.width - line, 0f), Size(line, size.height))
    if (isFirst) drawRect(color, Offset.Zero, Size(size.width, line))
    if (isLast) drawRect(color, Offset(0f, size.height - line), Size(size.width, line))
}
