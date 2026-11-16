package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel

// Dotted lines as in the mocks (`2px dotted`): square 2dp dots with 2dp gaps, drawn as
// rectangles so they stay crisp. Used for list dividers and the "next exercise" box.

/** A dotted 2dp line along the top edge (list row dividers on Home). */
fun Modifier.dottedTopLine(color: Color): Modifier = drawBehind {
    val dot = ArtPixel.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(color, Offset(x, 0f), Size(dot, dot))
        x += dot * 2
    }
}

/** A dotted 2dp line along the bottom edge (rows of the summary list). */
fun Modifier.dottedBottomLine(color: Color): Modifier = drawBehind {
    val dot = ArtPixel.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(color, Offset(x, size.height - dot), Size(dot, dot))
        x += dot * 2
    }
}

/** A dotted 2dp border on all four sides (the NEXT box on the active exercise screen). */
fun Modifier.dottedBorder(color: Color): Modifier = drawBehind {
    val dot = ArtPixel.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(color, Offset(x, 0f), Size(dot, dot))
        drawRect(color, Offset(x, size.height - dot), Size(dot, dot))
        x += dot * 2
    }
    var y = dot * 2
    while (y < size.height - dot) {
        drawRect(color, Offset(0f, y), Size(dot, dot))
        drawRect(color, Offset(size.width - dot, y), Size(dot, dot))
        y += dot * 2
    }
}
