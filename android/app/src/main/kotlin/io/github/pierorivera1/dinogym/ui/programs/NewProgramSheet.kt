package io.github.pierorivera1.dinogym.ui.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import io.github.pierorivera1.dinogym.data.seed.StarterKey
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/** What the new-program sheet can do. */
data class NewProgramActions(
    val onEmpty: () -> Unit,
    val onStarter: (StarterKey) -> Unit,
    val onClose: () -> Unit,
    /** FROM A LINK: the import screen with a field for a shared link (task 23). */
    val onFromLink: () -> Unit = {},
) {
    companion object {
        val None = NewProgramActions(onEmpty = {}, onStarter = {}, onClose = {})
    }
}

/**
 * `+ NEW` on YOUR PROGRAMS (mock `NewProgram.dc.html`, decision 92): a window sheet with an
 * EMPTY PROGRAM row, then READY-MADE starter cards (name with type and days, routine and
 * exercise counts, the cycle strip). Tapping a starter copies it in and opens its editor; EMPTY PROGRAM
 * opens a new empty program, as `+ NEW` did before task 22. FROM A LINK (task 23) opens the
 * import screen to paste a shared link.
 */
@Composable
internal fun NewProgramSheet(starters: List<StarterCardUi>, actions: NewProgramActions) {
    WindowSheet(stringResource(R.string.programs_new_sheet_title), actions.onClose, stringResource(R.string.cd_close)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HelpText(stringResource(R.string.programs_new_sheet_help))
            EmptyProgramRow(actions.onEmpty)
            SheetRow(stringResource(R.string.programs_new_from_link), stringResource(R.string.programs_new_from_link_meta), actions.onFromLink)
            SheetLabel(stringResource(R.string.programs_starters_label))
            starters.forEach { StarterCard(it, onClick = { actions.onStarter(it.key) }) }
            HelpText(stringResource(R.string.programs_starters_source))
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = DinoTheme.type.label, color = DinoTheme.colors.muted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun StarterCard(card: StarterCardUi, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    val meta = stringResource(
        R.string.programs_starter_meta,
        pluralStringResource(R.plurals.programs_routine_count, card.routineCount, card.routineCount),
        pluralStringResource(R.plurals.program_step_exercises, card.exerciseCount, card.exerciseCount),
    )
    val description = stringResource(R.string.programs_cd_add_starter, card.name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .raised(colors, pressed = false)
            .steppedBorder(colors.outline)
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    card.name.uppercase(),
                    style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp),
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
            }
            PixelIcon(R.drawable.ic_px_plus, colors.outline, size = 24.dp)
        }
        CompactCycleStrip(card.cycle, Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun EmptyProgramRow(onClick: () -> Unit) =
    SheetRow(stringResource(R.string.programs_new_empty), stringResource(R.string.programs_new_empty_meta), onClick)

/** A row with a title, a muted line and a chevron: EMPTY PROGRAM and FROM A LINK. */
@Composable
private fun SheetRow(title: String, meta: String, onClick: () -> Unit) {
    val colors = DinoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .raised(colors, pressed = false)
            .steppedBorder(colors.action)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = DinoTheme.type.button.copy(fontSize = 18.sp),
                color = colors.outline,
            )
            Text(meta, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
        }
        PixelIcon(R.drawable.ic_px_chevron, colors.outline, size = 24.dp)
    }
}
