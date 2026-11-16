package io.github.pierorivera1.dinogym.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.SectionHeader
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.Tag
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.home.shortDate
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat

/**
 * The Stats tab (mock `StatsHistory.dc.html`): the STATS title, the segmented control with
 * one box per [StatsTab], and the selected segment's [content] filling the rest. Stateless;
 * the bottom bar belongs to the app shell.
 */
@Composable
fun StatsScreen(
    tab: StatsTab,
    onSelectTab: (StatsTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DinoTheme.colors.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.nav_stats), isScreenTitle = true)
        SegmentedControl(
            options = StatsTab.entries.map { stringResource(it.label) },
            selectedIndex = tab.ordinal,
            onSelect = { onSelectTab(StatsTab.entries[it]) },
            asTabs = true,
        )
        content()
    }
}

/** HISTORY: finished sessions under month headings, newest first. Only the list scrolls. */
@Composable
fun ColumnScope.StatsHistoryContent(state: StatsHistoryUiState, onOpenSession: (sessionId: String) -> Unit) {
    if (state.isLoading) return
    if (state.months.isEmpty()) {
        EmptyMessage(stringResource(R.string.stats_history_empty))
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val colors = DinoTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        state.months.forEachIndexed { index, month ->
            item(key = month.month.toString()) {
                Text(
                    StatsFormat.monthHeading(month.month, locale),
                    style = DinoTheme.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = colors.muted,
                    modifier = Modifier
                        .padding(top = if (index == 0) 4.dp else 14.dp, bottom = 2.dp)
                        .semantics { heading() },
                )
            }
            items(month.rows, key = { it.sessionId }) { row -> HistoryRowItem(row, onClick = { onOpenSession(row.sessionId) }) }
        }
    }
}

@Composable
private fun HistoryRowItem(row: HistoryRow, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val sets = pluralStringResource(R.plurals.workout_sets, row.setCount, row.setCount)
    val bestTag = if (row.bestCount > 0) pluralStringResource(R.plurals.stats_best_tag, row.bestCount, row.bestCount) else null
    val open = stringResource(R.string.stats_open_session_cd, row.routineName, shortDate(row.date, locale))
    val description = listOfNotNull(open, sets, bestTag).joinToString(", ")
    ListRow(onClick, description) {
        Column(
            Modifier.size(48.dp).clip(SteppedShape).background(colors.surface2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                StatsFormat.weekday(row.date, locale),
                style = DinoTheme.type.label.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = colors.muted,
            )
            Text(row.date.dayOfMonth.toString(), style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 20.sp), color = colors.outline)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.routineName.uppercase(locale),
                style = DinoTheme.type.title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.stats_row_meta, WorkoutFormat.clock(row.durationSeconds), sets),
                style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = colors.muted,
                maxLines = 1,
            )
        }
        if (bestTag != null) Tag(bestTag)
        PixelIcon(R.drawable.ic_px_chevron, colors.muted, size = 24.dp)
    }
}

/**
 * PROGRESS: every active exercise with its best set 1, alphabetical like the library. A row
 * looks like a history row, with the exercise's icon tile in place of the date tile.
 */
@Composable
fun ColumnScope.ProgressListContent(state: ProgressListUiState, onOpenProgress: (exerciseId: String) -> Unit) {
    if (state.isLoading) return
    if (state.rows.isEmpty()) {
        EmptyMessage(stringResource(R.string.stats_progress_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        items(state.rows, key = { it.exerciseId }) { row -> ProgressRowItem(row, onClick = { onOpenProgress(row.exerciseId) }) }
    }
}

@Composable
private fun ProgressRowItem(row: ProgressListRow, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val bestText = row.best?.let { StatsFormat.setOne(it, row.usesWeight, locale) }
    val status = stringResource(if (row.best != null) R.string.stats_progress_best else R.string.stats_progress_none)
    val description = listOfNotNull(stringResource(R.string.stats_open_progress_cd, row.name), status, bestText).joinToString(", ")
    ListRow(onClick, description) {
        ExerciseTile(row.icon, size = 48.dp, iconSize = 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.name,
                style = DinoTheme.type.body.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status,
                style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = colors.muted,
                maxLines = 1,
            )
        }
        if (bestText != null) {
            Text(
                bestText,
                style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = colors.outline,
                maxLines = 1,
            )
        }
        PixelIcon(R.drawable.ic_px_chevron, colors.muted, size = 24.dp)
    }
}

/**
 * A tappable 64dp list row as in the history mock: `surface` fill, `surface2` border, stepped.
 * The whole row is one button; [description] is what a screen reader says for it.
 */
@Composable
private fun ListRow(onClick: () -> Unit, description: String, content: @Composable RowScope.() -> Unit) {
    val colors = DinoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.surface2)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The mascot's text box, for a list with nothing in it yet. */
@Composable
internal fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    TextBox(text, modifier = modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp), showMascot = true)
}
