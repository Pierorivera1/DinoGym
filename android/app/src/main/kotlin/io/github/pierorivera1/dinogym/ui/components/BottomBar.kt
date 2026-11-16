package io.github.pierorivera1.dinogym.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/** The four bottom bar tabs that open a screen. START is not one: it is an action. */
enum class BottomTab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_px_home),
    PROGRAMS(R.string.nav_programs, R.drawable.ic_px_programs),
    STATS(R.string.nav_stats, R.drawable.ic_px_stats),
    EXERCISES(R.string.nav_exercises, R.drawable.ic_px_exercises),
}

/**
 * The main navigation bar (docs/DESIGN.md "Bottom bar"): HOME, PROGRAMS, START, STATS,
 * EXERCISES as stepped, raised tabs. The [selected] tab uses the selected-state colours and
 * shows the cursor instead of its icon. START is taller and pokes above the bar, because
 * starting the next workout is the main thing the app is for. It handles the system
 * navigation bar inset itself, so its `surface` colour runs under the gesture bar.
 */
@Composable
fun BottomBar(
    selected: BottomTab,
    onTabClick: (BottomTab) -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawRect(colors.outline, Offset.Zero, Size(size.width, ArtPixel.toPx())) }
            .navigationBarsPadding()
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val tabModifier = Modifier.weight(1f).height(64.dp)
        Tab(BottomTab.HOME, selected, onTabClick, tabModifier)
        Tab(BottomTab.PROGRAMS, selected, onTabClick, tabModifier)
        val startLabel = stringResource(R.string.nav_start)
        RaisedBox(
            onClick = onStartClick,
            fill = colors.primary,
            border = colors.outline,
            // 76dp tall in a 64dp row, bottom-aligned, so the top 12dp rises above the other tabs.
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .height(76.dp)
                .semantics { contentDescription = startLabel },
        ) {
            TabContent(startLabel, colors.onPrimary) { PixelIcon(R.drawable.ic_px_start, colors.onPrimary) }
        }
        Tab(BottomTab.STATS, selected, onTabClick, tabModifier)
        Tab(BottomTab.EXERCISES, selected, onTabClick, tabModifier)
    }
}

@Composable
private fun Tab(tab: BottomTab, selected: BottomTab, onClick: (BottomTab) -> Unit, modifier: Modifier) {
    val colors = DinoTheme.colors
    val isSelected = tab == selected
    val label = stringResource(tab.label)
    val text = if (isSelected) colors.selText else colors.outline
    RaisedBox(
        onClick = { onClick(tab) },
        fill = if (isSelected) colors.selBg else colors.surface,
        border = if (isSelected) colors.selBorder else colors.action,
        role = Role.Tab,
        modifier = modifier.semantics {
            contentDescription = label
            this.selected = isSelected
        },
    ) {
        TabContent(label, text) {
            if (isSelected) Cursor(scale = 4) else PixelIcon(tab.icon, text)
        }
    }
}

/**
 * Icon above a one-line label. The label shrinks from 11sp to 8sp when it does not fit
 * (EJERCICIOS with a large system font), and never wraps or gets cut with an ellipsis.
 */
@Composable
private fun TabContent(label: String, color: Color, icon: @Composable () -> Unit) {
    Column(
        // The parent sets the content description, so the label is not read twice.
        modifier = Modifier.padding(horizontal = ArtPixel).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        BasicText(
            text = label,
            style = DinoTheme.type.navLabel.copy(color = color),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
    }
}
