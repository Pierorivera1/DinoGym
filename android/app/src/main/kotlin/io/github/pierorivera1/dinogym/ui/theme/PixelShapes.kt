package io.github.pierorivera1.dinogym.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** One art pixel. Borders are one art pixel thick (docs/DESIGN.md "Shapes and surfaces"). */
val ArtPixel = 2.dp

/**
 * A rectangle with a 2dp square cut out of each corner: the pixel-art "rounded" corner.
 * The same 12-point polygon as `N` in the mocks. Never use a corner radius instead.
 */
object SteppedShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { ArtPixel.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f); lineTo(w - c, c); lineTo(w, c)
            lineTo(w, h - c); lineTo(w - c, h - c); lineTo(w - c, h)
            lineTo(c, h); lineTo(c, h - c); lineTo(0f, h - c)
            lineTo(0f, c); lineTo(c, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A border drawn as four straight bars, one per edge, that stop [ArtPixel] short of each
 * corner. With [SteppedShape] this gives the mock's look: the corner pixel is empty and the
 * bars only touch diagonally. Compose's own `border()` would fill that corner pixel in.
 */
fun Modifier.steppedBorder(color: Color, width: Dp = ArtPixel): Modifier = drawBehind {
    val b = width.toPx()
    val c = ArtPixel.toPx()
    drawRect(color, Offset(c, 0f), Size(size.width - 2 * c, b))
    drawRect(color, Offset(c, size.height - b), Size(size.width - 2 * c, b))
    drawRect(color, Offset(0f, c), Size(b, size.height - 2 * c))
    drawRect(color, Offset(size.width - b, c), Size(b, size.height - 2 * c))
}

/**
 * The "raised" look: a 2dp [DinoColors.bandHigh] line inside the top border and a 4dp
 * [DinoColors.bandLow] band inside the bottom border. When [pressed], the bottom band goes
 * away; the caller also moves the component down 2dp (see the buttons). There is no ripple.
 */
fun Modifier.raised(colors: DinoColors, pressed: Boolean): Modifier = drawBehind {
    val border = ArtPixel.toPx()
    val inner = Size(size.width - 2 * border, 0f)
    drawRect(colors.bandHigh, Offset(border, border), inner.copy(height = 2.dp.toPx()))
    if (!pressed) {
        val low = 4.dp.toPx()
        drawRect(colors.bandLow, Offset(border, size.height - border - low), inner.copy(height = low))
    }
}

/**
 * A hard drop shadow: the same [shape], filled with [color], 4dp right and 4dp down, no blur.
 * Only for floating things (the active set card, the text box, dialogs). Put it before any
 * clip or background in the modifier chain, so it draws behind and outside the component.
 */
fun Modifier.hardShadow(color: Color, shape: Shape = RectangleShape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val offset = 4.dp.toPx()
    translate(offset, offset) { drawOutline(outline, color) }
}
