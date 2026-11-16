package io.github.pierorivera1.dinogym.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Left column for the y labels, and the room under the plot for the month labels. */
private val LabelColumn = 28.dp
private val PlotHeight = 168.dp
private val MonthRow = 28.dp

/** The top grid line sits this far below the plot's top; the lowest one this far above the axis. */
private val TopInset = 8.dp
private val BottomInset = 31.dp

/**
 * The stepped chart of set-1 records (DESIGN.md "Chart", mock `Progress.dc.html`), drawn with
 * Canvas: one step per record day, a 2dp `primary` line with a flat tint under it, dotted
 * grid lines, 4dp points and the latest point 8dp in `select`. The x axis has one equal slot
 * per month up to the current month. With weight the y axis is kilograms (a same-weight
 * record with more reps is then a flat step); without, reps.
 * [description] is what a screen reader says instead of the drawing.
 */
@Composable
fun ProgressChart(
    records: List<SetOneResult>,
    usesWeight: Boolean,
    today: LocalDate,
    zone: ZoneId,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val measurer = rememberTextMeasurer()
    val labelStyle = DinoTheme.type.body.copy(fontSize = 11.sp, lineHeight = 16.sp, color = colors.muted)
    val monthStyle = labelStyle.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)

    val points = remember(records, usesWeight, zone) {
        records.map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() to if (usesWeight) it.weightKg ?: 0.0 else it.reps.toDouble() }
    }
    if (points.isEmpty()) return
    val yAxis = remember(points, usesWeight) { ChartScale.yAxis(points.map { it.second }, whole = !usesWeight) }
    val xAxis = remember(points, today) { ChartScale.xAxis(points.first().first, points.last().first, today) }
    val tint = colors.primary.copy(alpha = if (colors.isNight) 0.20f else 0.15f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(PlotHeight + MonthRow)
            .semantics { contentDescription = description },
    ) {
        val left = LabelColumn.toPx()
        val plotWidth = size.width - left
        val top = TopInset.toPx()
        val lowest = PlotHeight.toPx() - BottomInset.toPx()
        val axisY = PlotHeight.toPx() - 1.dp.toPx()
        fun x(date: LocalDate) = left + (ChartScale.xFraction(xAxis, date) * plotWidth).toFloat()
        fun y(value: Double) = lowest - (ChartScale.yFraction(yAxis, value) * (lowest - top)).toFloat()

        // Grid lines and their labels.
        yAxis.ticks.forEach { tick ->
            dottedLine(y(tick), left, size.width, colors.muted.copy(alpha = 0.4f))
            val text = if (usesWeight) WorkoutFormat.kg(tick, locale) else tick.toInt().toString()
            drawLabel(measurer, text, labelStyle, rightEdge = left - 8.dp.toPx(), centreY = y(tick))
        }
        drawLine(colors.muted, Offset(left, axisY), Offset(size.width, axisY), strokeWidth = 2.dp.toPx())

        // The tint under the steps, then the stepped line.
        val xs = points.map { x(it.first) }
        val ys = points.map { y(it.second) }
        if (points.size > 1) {
            val line = Path().apply {
                moveTo(xs[0], ys[0])
                for (i in 1 until xs.size) {
                    lineTo(xs[i], ys[i - 1])
                    lineTo(xs[i], ys[i])
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(xs.last(), axisY)
                lineTo(xs.first(), axisY)
                close()
            }
            drawPath(area, tint)
            drawPath(line, colors.primary, style = Stroke(width = 2.dp.toPx()))
        }

        // Points: 4dp squares, the latest 8dp in select with an outline border.
        val small = 4.dp.toPx()
        for (i in 0 until xs.lastIndex) {
            drawRect(colors.primary, Offset(xs[i] - small / 2, ys[i] - small / 2), Size(small, small))
        }
        val big = 8.dp.toPx()
        val border = 2.dp.toPx()
        drawRect(colors.outline, Offset(xs.last() - big / 2 - border / 2, ys.last() - big / 2 - border / 2), Size(big + border, big + border))
        drawRect(colors.select, Offset(xs.last() - big / 2 + border / 2, ys.last() - big / 2 + border / 2), Size(big - border, big - border))

        // Month labels, centred in their slots.
        val slot = plotWidth / xAxis.months.size
        xAxis.months.forEachIndexed { index, month ->
            if (!ChartScale.isLabelled(xAxis, index)) return@forEachIndexed
            val layout = measurer.measure(StatsFormat.shortMonth(month, locale), monthStyle)
            val cx = left + slot * (index + 0.5f)
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, PlotHeight.toPx() + 8.dp.toPx()))
        }
    }
}

/** A 2dp dotted horizontal line: 2dp dots with 4dp gaps, as the mock's grid. */
private fun DrawScope.dottedLine(y: Float, from: Float, to: Float, color: Color) {
    val dot = 2.dp.toPx()
    var x = from
    while (x < to) {
        drawRect(color, Offset(x, y - dot / 2), Size(dot, dot))
        x += dot * 3
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, rightEdge: Float, centreY: Float) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(rightEdge - layout.size.width, centreY - layout.size.height / 2f))
}
