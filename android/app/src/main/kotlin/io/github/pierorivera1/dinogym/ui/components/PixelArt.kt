package io.github.pierorivera1.dinogym.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import io.github.pierorivera1.dinogym.R
import kotlin.math.roundToInt

/*
 * Pixel art stays crisp only when every art pixel covers a whole number of screen pixels.
 * Phone densities are often fractional (2.625x), so a 2dp art pixel would be 5.25 screen
 * pixels and its edges would blur. Each composable here rounds the art pixel to whole screen
 * pixels and centres the result in its box, so the art can be a few pixels off its exact size.
 */

/** Screen pixels per art pixel, rounded to a whole number, at least 1. */
private fun DrawScope.wholePixels(artPixel: Dp): Float = artPixel.toPx().roundToInt().coerceAtLeast(1).toFloat()

/** Top-left corner that centres a square of [side] pixels in the canvas, on a whole pixel. */
private fun DrawScope.centred(side: Float) =
    Offset(((size.width - side) / 2).roundToInt().toFloat(), ((size.height - side) / 2).roundToInt().toFloat())

/**
 * One of our 16x16 icons (`R.drawable.ic_px_*`), drawn in a single [tint] at [scale]x
 * (2 = 32dp, 3 = 48dp). Decorative unless a [contentDescription] is given.
 */
@Composable
fun PixelIcon(
    @DrawableRes icon: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    scale: Int = 2,
    contentDescription: String? = null,
) {
    val painter = painterResource(icon)
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.size((16 * scale).dp).then(semantics)) {
        val side = 16 * wholePixels(scale.dp)
        translate(centred(side).x, centred(side).y) {
            with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
        }
    }
}

// Dinocito's 8x8 face, from CUR_O and CUR_Y in the mocks. 'O' = outline, 'Y' = yellow.
private val CursorRows = listOf(
    "..OOOO..",
    ".OYYYYO.",
    "OYYYYYYO",
    "OYOYYOYO",
    "OYYYYYYO",
    "OYYOOYYO",
    ".OYYYYO.",
    "..OOOO..",
)
private val CursorYellow = Color(0xFFF5D23A)
private val CursorOutline = Color(0xFF33213F)

/**
 * The selection cursor: Dinocito's 8x8 face, drawn at [scale]x (2 to 4). It marks the
 * current item and replaces the icon on the selected bottom bar tab. Its colours are the
 * same in both themes, as in the mocks. Always decorative: the item it marks has the label.
 */
@Composable
fun Cursor(modifier: Modifier = Modifier, scale: Int = 4) {
    Canvas(modifier.size((8 * scale).dp)) {
        val p = wholePixels(scale.dp)
        val origin = centred(8 * p)
        CursorRows.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                val color = when (cell) {
                    'O' -> CursorOutline
                    'Y' -> CursorYellow
                    else -> return@forEachIndexed
                }
                drawRect(color, origin + Offset(x * p, y * p), Size(p, p))
            }
        }
    }
}

/**
 * Dinocito, the mascot (32x32 art pixels), drawn with nearest-neighbour scaling so the
 * pixels stay sharp. Decorative: the text next to it carries the meaning.
 */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val image: ImageBitmap = ImageBitmap.imageResource(R.drawable.dinocito)
    Canvas(modifier.size(size)) {
        val p = wholePixels(size / image.width)
        val side = image.width * p
        val origin = centred(side)
        drawImage(
            image = image,
            dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
            dstSize = IntSize(side.toInt(), side.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}
