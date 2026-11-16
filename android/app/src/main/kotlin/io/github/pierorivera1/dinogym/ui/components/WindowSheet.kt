package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * A bottom sheet styled as a pixel window (DESIGN.md "Window sheet"): a title bar
 * (`surface2` in Night, `teal` in Day) with a square close box, the [content] on `surface`,
 * and the `scrim` over the screen behind. Tapping the scrim or the close box calls [onClose].
 *
 * It is drawn inside the screen, over its content, instead of in a separate window, so a
 * screenshot test can render it. The screen handles system Back (it closes the sheet).
 *
 * With [fullHeight] the sheet reaches 40dp below the status bar, and [content] fills the
 * rest (use `Modifier.weight` inside it), as the exercise picker does.
 */
@Composable
fun WindowSheet(
    title: String,
    onClose: () -> Unit,
    closeDescription: String,
    modifier: Modifier = Modifier,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DinoTheme.colors
    Box(modifier.fillMaxSize()) {
        // The scrim. It has no semantics of its own: the close box is the accessible way out.
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(interactionSource = null, indication = null, onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (fullHeight) Modifier.statusBarsPadding().padding(top = 40.dp).fillMaxHeight() else Modifier)
                .background(colors.surface)
                .drawBehind { drawRect(colors.outline, Offset.Zero, Size(size.width, ArtPixel.toPx())) }
                .padding(top = ArtPixel)
                .navigationBarsPadding()
                .imePadding()
                .semantics { paneTitle = title },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.teal ?: colors.surface2)
                    .drawBehind {
                        val line = ArtPixel.toPx()
                        drawRect(colors.outline, Offset(0f, size.height - line), Size(size.width, line))
                    }
                    .padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = DinoTheme.type.title.copy(fontSize = 20.sp, lineHeight = 24.sp), color = colors.outline)
                Box(
                    Modifier
                        .size(44.dp)
                        .background(colors.surface)
                        .border(ArtPixel, colors.outline)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = closeDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(R.drawable.ic_px_close, colors.outline, size = 24.dp)
                }
            }
            Column(
                (if (fullHeight) Modifier.weight(1f) else Modifier).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}
