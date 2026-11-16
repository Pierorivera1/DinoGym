package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Height of one row in a wheel. Five rows show: two above the selection, two below. */
private val RowHeight = 44.dp
private const val VISIBLE_ROWS = 5

/**
 * The frame of the scroll picker (DESIGN.md "Scroll picker"): a double border on `bg`, one
 * column header per wheel, and the stepped `select` band with the cursor across the middle.
 * The [wheels] go side by side inside it, each with `Modifier.weight(1f)`. [labels] are the
 * column headers (`MIN`, `SEC`).
 */
@Composable
fun ScrollPicker(
    labels: List<String>,
    modifier: Modifier = Modifier,
    wheels: @Composable RowScope.() -> Unit,
) {
    val colors = DinoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bg)
            .border(ArtPixel, colors.outline)
            .padding(ArtPixel * 2)
            .border(ArtPixel, colors.surface)
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(18.dp)) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = DinoTheme.type.label.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(RowHeight * VISIBLE_ROWS)) {
            // The selection band: 60dp tall, centred, inset 4dp from the frame.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(60.dp)
                    .clip(SteppedShape)
                    .background(if (colors.isNight) colors.select.copy(alpha = 0.10f) else colors.select)
                    .steppedBorder(colors.selBorder),
            )
            Cursor(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), scale = 3)
            Row(Modifier.fillMaxWidth(), content = wheels)
        }
    }
}

/**
 * One wheel of the [ScrollPicker]: a list of [values] that scrolls and snaps so one value
 * sits in the band. The value in the band is [selectedIndex]; when the user stops on another
 * one, [onSelect] gets its index. Neighbours shrink and fade (40 → 26 → 18sp,
 * `outline` → `muted` → `faint`). Tapping a value also selects it, which is how screen
 * reader users change it. [label] names the wheel for screen readers.
 */
@Composable
fun PickerWheel(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    val centred by remember {
        derivedStateOf {
            val extra = if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            (state.firstVisibleItemIndex + extra).coerceIn(0, values.lastIndex)
        }
    }
    val latestOnSelect by rememberUpdatedState(onSelect)

    // Report the value once the wheel stops on it.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centred }
            .filter { (scrolling, _) -> !scrolling }
            .distinctUntilChanged()
            .collect { (_, index) -> latestOnSelect(index) }
    }
    // Follow changes made elsewhere (the 1:30 and 2:30 shortcuts).
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && centred != selectedIndex) state.animateScrollToItem(selectedIndex)
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = RowHeight * (VISIBLE_ROWS / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(RowHeight * VISIBLE_ROWS)
            .semantics {
                contentDescription = label
                stateDescription = values.getOrElse(selectedIndex) { "" }
            },
    ) {
        itemsIndexed(values) { index, value ->
            val distance = abs(index - centred)
            val size = when (distance) {
                0 -> 40.sp
                1 -> 26.sp
                else -> 18.sp
            }
            val color = when (distance) {
                0 -> colors.outline
                1 -> colors.muted
                else -> colors.faint
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .clickable(role = Role.Button) {
                        onSelect(index)
                        scope.launch { state.animateScrollToItem(index) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(value, style = DinoTheme.type.title.copy(fontSize = size, lineHeight = size), color = color)
            }
        }
    }
}
