package com.dinogym.ui.main

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val DragHandle = icon("Reorder") {
        for (x in listOf(9f, 15f)) for (y in listOf(6f, 12f, 18f)) {
            moveTo(x, y); lineTo(x, y + .1f)
        }
    }
    val More = icon("More options") {
        moveTo(12f, 5f); lineTo(12f, 5.1f)
        moveTo(12f, 12f); lineTo(12f, 12.1f)
        moveTo(12f, 19f); lineTo(12f, 19.1f)
    }
    val Train = icon("Train") {
        moveTo(3f, 9f); lineTo(3f, 15f); moveTo(6f, 6f); lineTo(6f, 18f)
        moveTo(6f, 12f); lineTo(18f, 12f); moveTo(18f, 6f); lineTo(18f, 18f)
        moveTo(21f, 9f); lineTo(21f, 15f)
    }
    val Progress = icon("Progress") {
        moveTo(4f, 4f); lineTo(4f, 20f); lineTo(21f, 20f)
        moveTo(8f, 15f); lineTo(12f, 11f); lineTo(16f, 13f); lineTo(21f, 6f)
    }
    val Library = icon("Exercises") {
        moveTo(7f, 5f); lineTo(20f, 5f); moveTo(7f, 12f); lineTo(20f, 12f)
        moveTo(7f, 19f); lineTo(20f, 19f)
        moveTo(3f, 5f); lineTo(3.1f, 5f); moveTo(3f, 12f); lineTo(3.1f, 12f)
        moveTo(3f, 19f); lineTo(3.1f, 19f)
    }
    val Arrow = icon("Arrow") {
        moveTo(5f, 12f); lineTo(19f, 12f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }
    val Back = icon("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
    val Add = icon("Add") {
        moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val Timer = icon("Rest timer") {
        moveTo(9f, 2f); lineTo(15f, 2f); moveTo(12f, 2f); lineTo(12f, 5f)
        moveTo(20f, 13f)
        curveTo(20f, 17.4f, 16.4f, 21f, 12f, 21f)
        curveTo(7.6f, 21f, 4f, 17.4f, 4f, 13f)
        curveTo(4f, 8.6f, 7.6f, 5f, 12f, 5f)
        curveTo(16.4f, 5f, 20f, 8.6f, 20f, 13f)
        moveTo(12f, 9f); lineTo(12f, 13f); lineTo(15f, 15f)
        moveTo(18f, 5f); lineTo(20f, 7f)
    }
    val Edit = icon("Edit") {
        moveTo(4f, 16.5f); lineTo(3f, 21f); lineTo(7.5f, 20f); lineTo(19f, 8.5f)
        lineTo(14.5f, 4f); close(); moveTo(12.5f, 6f); lineTo(17f, 10.5f)
    }
    val Archive = icon("Archive") {
        moveTo(4f, 4f); lineTo(20f, 4f); lineTo(21f, 8f); lineTo(3f, 8f); close()
        moveTo(5f, 8f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(19f, 8f)
        moveTo(9f, 12f); lineTo(15f, 12f)
    }
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
}

internal fun androidx.compose.ui.Modifier.semanticsLabel(label: String) = this.then(
    androidx.compose.ui.Modifier.semantics { contentDescription = label }
)
