package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.hardShadow
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/*
 * Reordering a short list (cycle steps, a routine's exercises) without a library.
 *
 * How it works: long-press the drag handle, then drag. The dragged row follows the finger
 * (a translation, drawn above the others). When it passes the middle of a neighbour, the two
 * swap places in a local copy of the list. On release, the caller gets one onMove(from, to)
 * and saves it; the local order stays on screen until the caller's new list arrives, so the
 * row does not jump back for a frame. Screen readers get "Move up" / "Move down" actions on
 * the handle instead of the gesture.
 *
 * Limits: it is a plain Column (no LazyColumn), meant for lists of a few dozen rows at most,
 * and it does not scroll the screen while dragging near an edge.
 */

/** This list with the item at [from] moved to [to]; the items between shift by one. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/**
 * Whether a row dragged [offset] pixels from its place should swap with a neighbour:
 * +1 with the next row once it is past half of [nextSize], -1 with the previous row once it
 * is past half of [previousSize], else 0. A size is the neighbour's height plus the gap;
 * null when there is no neighbour on that side.
 */
fun reorderStep(offset: Float, previousSize: Float?, nextSize: Float?): Int = when {
    nextSize != null && offset > nextSize / 2 -> 1
    previousSize != null && offset < -previousSize / 2 -> -1
    else -> 0
}

/**
 * The drag state of one [ReorderableColumn]. It lives as long as the column, so the gesture
 * code (which starts once per row) always talks to the same object and never sees stale data.
 */
internal class ReorderController<T>(private val keyOf: (T) -> Any) {
    /** The caller's latest list and callback, updated on every composition. */
    var items: List<T> = emptyList()
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var spacingPx = 0f

    /** Row heights in pixels by key, measured on screen. */
    val heights = mutableMapOf<Any, Int>()

    /** The order on screen while dragging, and after a drop until the caller's list changes. */
    private var order by mutableStateOf<List<T>?>(null)
    private var waitingFor by mutableStateOf<List<T>?>(null)
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun shown(current: List<T>): List<T> {
        val local = order ?: return current
        return if (draggedKey != null || current === waitingFor) local else current
    }

    fun start(key: Any) {
        order = shown(items)
        waitingFor = null
        draggedKey = key
        offset = 0f
    }

    fun drag(dy: Float) {
        val list = order ?: return
        val key = draggedKey ?: return
        offset += dy
        val i = list.indexOfFirst { keyOf(it) == key }
        val previous = list.getOrNull(i - 1)?.let { size(it) }
        val next = list.getOrNull(i + 1)?.let { size(it) }
        when (reorderStep(offset, previous, next)) {
            1 -> { order = list.moved(i, i + 1); offset -= next!! }
            -1 -> { order = list.moved(i, i - 1); offset += previous!! }
        }
    }

    fun end() {
        val list = order
        val key = draggedKey
        draggedKey = null
        offset = 0f
        if (list == null || key == null) return
        val from = items.indexOfFirst { keyOf(it) == key }
        val to = list.indexOfFirst { keyOf(it) == key }
        if (from >= 0 && to >= 0 && from != to) {
            waitingFor = items
            onMove(from, to)
        } else {
            order = null
        }
    }

    /** The accessibility actions: move the row at [index] one place up (-1) or down (+1). */
    fun moveBy(index: Int, delta: Int): Boolean {
        val to = index + delta
        if (index !in items.indices || to !in items.indices) return false
        onMove(index, to)
        return true
    }

    private fun size(item: T): Float? = heights[keyOf(item)]?.let { it + spacingPx }
}

/**
 * What a row of a [ReorderableColumn] knows: its [index], the row [count], and whether it
 * [isDragging]. Put [dragHandle] on the part the user holds (the [ReorderableRow] does it).
 */
class ReorderItemScope internal constructor(
    val index: Int,
    val count: Int,
    val isDragging: Boolean,
    private val key: Any,
    private val controller: ReorderController<*>,
    private val haptics: HapticFeedback,
    private val moveUpLabel: String,
    private val moveDownLabel: String,
) {
    /**
     * Makes this element the drag handle: long-press then drag to move the row; screen readers
     * read [contentDescription] and get Move up / Move down actions (where there is room).
     */
    fun Modifier.dragHandle(contentDescription: String): Modifier = this
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.start(key)
                },
                onDragEnd = { controller.end() },
                onDragCancel = { controller.end() },
                onDrag = { change, amount ->
                    change.consume()
                    controller.drag(amount.y)
                },
            )
        }
        .semantics {
            this.contentDescription = contentDescription
            customActions = buildList {
                if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { controller.moveBy(index, -1) })
                if (index < count - 1) add(CustomAccessibilityAction(moveDownLabel) { controller.moveBy(index, 1) })
            }
        }
}

/**
 * A column whose rows can be reordered by dragging (see the notes at the top of this file).
 * [key] must be unique and stable per item (its id). [onMove] gets the indexes in [items]
 * and must save the new order; the column shows the new order until [items] changes.
 * Each row is drawn by [itemContent], usually a [ReorderableRow].
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    itemContent: @Composable ReorderItemScope.(index: Int, item: T) -> Unit,
) {
    val controller = remember { ReorderController(key) }
    controller.items = items
    controller.onMove = onMove
    controller.spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.cd_move_up)
    val moveDown = stringResource(R.string.cd_move_down)

    val shown = controller.shown(items)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        shown.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val dragging = itemKey == controller.draggedKey
                val scope = ReorderItemScope(index, shown.size, dragging, itemKey, controller, haptics, moveUp, moveDown)
                Box(
                    Modifier
                        .onSizeChanged { controller.heights[itemKey] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) controller.offset else 0f },
                ) {
                    scope.itemContent(index, item)
                }
            }
        }
    }
}

/**
 * A list row with a drag handle (DESIGN.md "Reorderable row"): a 40dp handle with the drag
 * icon in `muted` on the left, the [content], and, when [onRemove] is set, a 48dp ✕ button
 * on the right (`surface2` border, `muted` icon). At least 56dp tall, `surface` fill, so the
 * row stays opaque while it is dragged over the others; the dragged row gets the hard shadow.
 * [dragDescription] is what a screen reader says for the handle ("Move step 1, Pull").
 */
@Composable
fun ReorderItemScope.ReorderableRow(
    dragDescription: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    removeDescription: String = stringResource(R.string.cd_remove),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = DinoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (isDragging) Modifier.hardShadow(colors.shadow) else Modifier)
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .heightIn(min = 56.dp)
                .dragHandle(dragDescription),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(R.drawable.ic_px_drag, colors.muted, size = 24.dp)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
        if (onRemove != null) {
            Box(
                Modifier
                    .padding(start = 8.dp, end = 4.dp)
                    .size(48.dp)
                    .clip(SteppedShape)
                    .steppedBorder(colors.surface2)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = removeDescription },
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(R.drawable.ic_px_close, colors.muted, size = 24.dp)
            }
        }
    }
}
