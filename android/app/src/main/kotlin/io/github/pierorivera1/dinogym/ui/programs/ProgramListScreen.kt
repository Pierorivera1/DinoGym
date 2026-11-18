package io.github.pierorivera1.dinogym.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.ui.components.Cursor
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.SectionHeader
import io.github.pierorivera1.dinogym.ui.components.Tag
import io.github.pierorivera1.dinogym.ui.components.TagStyle
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.home.CycleCell
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * The Programs tab (mock `ProgramList.dc.html`): the title, then YOUR PROGRAMS and ROUTINES,
 * each with `+ NEW` in its header. The active program comes first in the selected-state
 * colours with the cursor, an ACTIVE tag and its cycle strip with the next step marked.
 * Tapping a program or a routine opens its editor; `+ NEW` program opens [NewProgramSheet].
 * Stateless; the bottom bar belongs to the app shell.
 */
@Composable
fun ProgramListScreen(
    state: ProgramListUiState,
    onNewProgram: () -> Unit,
    onNewRoutine: () -> Unit,
    onOpenProgram: (programId: String) -> Unit,
    onOpenRoutine: (routineId: String) -> Unit,
    modifier: Modifier = Modifier,
    newProgramActions: NewProgramActions = NewProgramActions.None,
) {
    Box(modifier.fillMaxSize()) {
        ProgramListContent(state, onNewProgram, onNewRoutine, onOpenProgram, onOpenRoutine)
        if (state.newProgramSheet) NewProgramSheet(state.starters, newProgramActions)
    }
}

@Composable
private fun ProgramListContent(
    state: ProgramListUiState,
    onNewProgram: () -> Unit,
    onNewRoutine: () -> Unit,
    onOpenProgram: (programId: String) -> Unit,
    onOpenRoutine: (routineId: String) -> Unit,
) {
    val colors = DinoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.programs_title), isScreenTitle = true)
        if (state.isLoading) return@Column

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = stringResource(R.string.programs_section),
                onNew = onNewProgram,
                newContentDescription = stringResource(R.string.programs_new_cd),
            )
            if (state.programs.isEmpty()) EmptyText(stringResource(R.string.programs_empty))
            state.programs.forEach { ProgramCard(it, onClick = { onOpenProgram(it.id) }) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = stringResource(R.string.programs_routines_section),
                onNew = onNewRoutine,
                newContentDescription = stringResource(R.string.programs_new_routine_cd),
                newText = stringResource(R.string.programs_new_routine_button),
            )
            if (state.routines.isEmpty()) {
                EmptyText(stringResource(R.string.routines_empty))
            } else {
                RoutineList(state.routines, onOpenRoutine)
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = DinoTheme.type.body.copy(fontSize = 15.sp), color = DinoTheme.colors.muted)
}

/** The type in sentence case, for the card's meta line. */
@Composable
internal fun typeName(type: ProgramType): String = stringResource(
    if (type == ProgramType.GYM) R.string.programs_type_gym else R.string.programs_type_calisthenics,
)

/**
 * A program card: raised, `sel*` colours when active. Row of cursor (active only), name with
 * the ACTIVE tag, the meta line ("Calisthenics · 6 steps · next: Pull") and a 44dp edit tile;
 * under it the compact cycle strip.
 */
@Composable
private fun ProgramCard(card: ProgramCardUi, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    val fg = if (card.isActive) colors.selText else colors.outline
    val steps = pluralStringResource(R.plurals.programs_steps, card.stepCount, card.stepCount)
    val meta = if (card.isActive && card.stepCount > 0) {
        val next = card.nextRoutine ?: stringResource(R.string.programs_next_rest)
        stringResource(R.string.programs_meta_next, typeName(card.type), steps, next)
    } else {
        stringResource(R.string.programs_meta, typeName(card.type), steps)
    }
    val description = stringResource(
        if (card.isActive) R.string.programs_cd_edit_program_active else R.string.programs_cd_edit_program,
        card.name,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(if (card.isActive) colors.selBg else colors.surface)
            .raised(colors, pressed = false)
            .steppedBorder(if (card.isActive) colors.selBorder else colors.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$description. $meta" }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (card.isActive) Cursor(scale = 3)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.name.uppercase(),
                        style = DinoTheme.type.title.copy(fontSize = 22.sp, lineHeight = 24.sp),
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (card.isActive) Tag(stringResource(R.string.programs_active), style = TagStyle.Outlined, color = fg)
                }
                Text(
                    meta,
                    style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = if (card.isActive && !colors.isNight) colors.outline else colors.muted,
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(SteppedShape)
                    .background(colors.surface2)
                    .steppedBorder(colors.outline),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(R.drawable.ic_px_edit, colors.outline, size = 24.dp)
            }
        }
        if (card.cycle.isNotEmpty()) CompactCycleStrip(card.cycle, Modifier.clearAndSetSemantics {})
    }
}

/**
 * The cycle strip inside a program card: 32dp boxes, 11sp labels (shrinking to 8sp), the
 * cursor on the current step. Smaller than Home's [io.github.pierorivera1.dinogym.ui.components.CycleStrip].
 */
@Composable
internal fun CompactCycleStrip(cells: List<CycleCell>, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    val rest = stringResource(R.string.home_cycle_rest)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.forEach { cell ->
            val text = when {
                cell.isCurrent -> colors.selText
                cell.routineName == null -> colors.muted
                else -> colors.outline
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(SteppedShape)
                    .background(colors.surface)
                    .steppedBorder(if (cell.isCurrent) colors.selBorder else colors.surface2)
                    .padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cell.isCurrent) Cursor(scale = 2)
                BasicText(
                    text = cell.routineName?.uppercase() ?: rest,
                    style = DinoTheme.type.button.copy(fontSize = 11.sp, lineHeight = 12.sp, color = text),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                )
            }
        }
    }
}

/** The routines as one bordered `surface` box with dotted dividers: name, size, chevron. */
@Composable
private fun RoutineList(routines: List<RoutineRowUi>, onOpen: (String) -> Unit) {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline)
            .padding(2.dp),
    ) {
        routines.forEachIndexed { index, routine ->
            val meta = stringResource(
                R.string.home_meta,
                pluralStringResource(R.plurals.home_exercise_count, routine.exerciseCount, routine.exerciseCount),
                pluralStringResource(R.plurals.home_set_count, routine.setCount, routine.setCount),
            )
            val description = stringResource(R.string.programs_cd_edit_routine, routine.name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .then(if (index < routines.lastIndex) Modifier.dottedBottomLine(colors.surface2) else Modifier)
                    .clickable(role = Role.Button) { onOpen(routine.id) }
                    .clearAndSetSemantics { contentDescription = "$description. $meta" }
                    .padding(start = 12.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    routine.name.uppercase(),
                    style = DinoTheme.type.title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(min = 72.dp, max = 150.dp),
                )
                Text(
                    meta,
                    style = DinoTheme.type.body.copy(fontSize = 14.sp),
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PixelIcon(R.drawable.ic_px_chevron, colors.outline, size = 24.dp)
            }
        }
    }
}
