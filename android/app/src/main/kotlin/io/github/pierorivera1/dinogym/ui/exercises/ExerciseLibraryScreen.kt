package io.github.pierorivera1.dinogym.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.ExerciseTile
import io.github.pierorivera1.dinogym.ui.components.SectionHeader
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * The exercise library (mock `ExerciseLibrary.dc.html`): the title with `+ NEW`, the
 * ACTIVE / ARCHIVED filter with counts, and a three-column grid of tiles (icon and name).
 * Only the grid scrolls. Stateless: it draws [state] and reports taps. The bottom bar
 * belongs to the app shell.
 */
@Composable
fun ExerciseLibraryScreen(
    state: ExerciseLibraryUiState,
    onNew: () -> Unit,
    onSelectFilter: (LibraryFilter) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.exercises_title),
            isScreenTitle = true,
            onNew = onNew,
            newContentDescription = stringResource(R.string.exercises_new_cd),
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.exercises_filter_active, state.activeCount),
                stringResource(R.string.exercises_filter_archived, state.archivedCount),
            ),
            selectedIndex = state.filter.ordinal,
            onSelect = { onSelectFilter(LibraryFilter.entries[it]) },
            asTabs = true,
        )
        if (state.isLoading) return@Column
        if (state.tiles.isEmpty()) {
            Text(
                text = stringResource(
                    if (state.filter == LibraryFilter.ACTIVE) R.string.exercises_empty_active else R.string.exercises_empty_archived,
                ),
                style = DinoTheme.type.body.copy(fontSize = 15.sp),
                color = colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(state.tiles, key = { it.id }) { tile ->
                ExerciseGridTile(tile, onClick = { onOpenExercise(tile.id) })
            }
        }
    }
}

/**
 * One exercise in the grid: a 128dp `surface` box with a `surface2` border, the 56dp icon
 * tile (icon at 3x) and the name under it, up to three lines. The whole tile is the button;
 * its name is what a screen reader says.
 */
@Composable
private fun ExerciseGridTile(tile: LibraryTile, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExerciseTile(tile.icon, size = 56.dp, iconSize = 48.dp)
        Text(
            text = tile.name,
            style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = colors.outline,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
