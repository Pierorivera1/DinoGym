package io.github.pierorivera1.dinogym.ui.bodyweight

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.ui.theme.DinoColors
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Dark gold for the goal line in Day: the pale `select` is 1.25:1 on cream (PIXEL-STYLE.md "Body weight chart"). */
private val DayGoalLine = Color(0xFFB07A00)

/** The goal line colour: `select` in Night, dark gold in Day. */
internal fun goalLineColor(colors: DinoColors): Color = if (colors.isNight) colors.select else DayGoalLine

/**
 * The body weight chart (decision 32, DESIGN.md "Chart"), drawn with Canvas in the pixel
 * style: dotted grid lines at whole kg, the 7-day average as a stepped 2dp `primary` line with
 * a flat tint under it, raw weigh-ins as 4dp `muted` squares, the goal as a dashed line with
 * its value at the right end, and the latest average as an 8dp `select` square. Month labels
 * sit under the axis. Screen readers get [summary] instead of the drawing.
 */
@Composable
internal fun WeightChartCanvas(chart: WeightChart, summary: String, locale: Locale, modifier: Modifier = Modifier) {
    val colors = DinoTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = DinoTheme.type.body.copy(fontSize = 11.sp, lineHeight = 16.sp, color = colors.muted)
    val monthStyle = DinoTheme.type.label.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.04.em, color = colors.muted)
    val goalStyle = DinoTheme.type.button.copy(fontSize = 13.sp, lineHeight = 16.sp, color = if (colors.isNight) colors.select else colors.outline)
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(196.dp)
            .semantics { contentDescription = summary },
    ) {
        val art = 2.dp.toPx().roundToInt().toFloat()
        val plotLeft = 32.dp.toPx()
        val plotRight = size.width - 6.dp.toPx()
        val plotTop = 12.dp.toPx()
        val axisY = (170.dp.toPx()).roundToInt().toFloat()
        val plotBottom = axisY - 6.dp.toPx()
        val days = max(1L, chart.endDay.toEpochDay() - chart.startDay.toEpochDay()).toFloat()
        val kgSpan = (chart.maxKg - chart.minKg).toFloat()

        fun x(day: LocalDate): Float =
            (plotLeft + (day.toEpochDay() - chart.startDay.toEpochDay()) / days * (plotRight - plotLeft)).roundToInt().toFloat()

        fun y(kg: Double): Float =
            (plotTop + (chart.maxKg - kg).toFloat() / kgSpan * (plotBottom - plotTop)).roundToInt().toFloat()

        // Grid: a dotted 2dp line at each whole-kg label, `muted` at 40%, with the label on the left.
        chart.yTicks.forEach { kg ->
            val gy = y(kg.toDouble())
            dottedLine(colors.muted.copy(alpha = 0.4f), plotLeft - 4.dp.toPx(), size.width, gy, art, dash = art, gap = 2 * art)
            val text = measurer.measure(kg.toString(), labelStyle)
            drawText(text, topLeft = Offset(24.dp.toPx() - text.size.width, gy - text.size.height / 2f))
        }

        val avg = chart.average.map { Offset(x(it.date), y(it.kg)) }

        // Flat tint under the stepped average line.
        if (avg.size >= 2) {
            val fill = Path().apply {
                moveTo(avg.first().x, axisY)
                lineTo(avg.first().x, avg.first().y)
                for (i in 1 until avg.size) {
                    lineTo(avg[i].x, avg[i - 1].y)
                    lineTo(avg[i].x, avg[i].y)
                }
                lineTo(avg.last().x, axisY)
                close()
            }
            drawPath(fill, colors.primary.copy(alpha = if (colors.isNight) 0.20f else 0.15f))
        }

        // Goal: dashes 3 art pixels long with 2-pixel gaps, above the fill, below the line.
        chart.goalKg?.let { goal ->
            val gy = y(goal)
            dottedLine(goalLineColor(colors), plotLeft, size.width, gy, art, dash = 3 * art, gap = 2 * art)
            val text = measurer.measure(BodyWeightStateMapper.kgText(goal, locale), goalStyle)
            val above = gy - text.size.height - art
            drawText(text, topLeft = Offset(size.width - text.size.width - art, if (above > 0) above else gy + art * 2))
        }

        // Raw weigh-ins: 4dp squares.
        val point = 2 * art
        chart.raw.forEach { p ->
            drawRect(colors.muted, Offset(x(p.date) - point / 2, y(p.kg) - point / 2), Size(point, point))
        }

        // The 7-day average as a stepped line: across to the next day, then up or down.
        for (i in 1 until avg.size) {
            val a = avg[i - 1]
            val b = avg[i]
            drawRect(colors.primary, Offset(min(a.x, b.x), a.y - art / 2), Size(abs(b.x - a.x) + art, art))
            drawRect(colors.primary, Offset(b.x - art / 2, min(a.y, b.y) - art / 2), Size(art, abs(b.y - a.y) + art))
        }

        // The x axis.
        drawRect(colors.muted, Offset(plotLeft - 4.dp.toPx(), axisY), Size(size.width - plotLeft + 4.dp.toPx(), art))

        // The latest average: 8dp `select` with an `outline` border.
        avg.lastOrNull()?.let { last ->
            val side = 4 * art
            val topLeft = Offset(last.x - side / 2, last.y - side / 2)
            drawRect(colors.outline, topLeft, Size(side, side))
            drawRect(colors.select, topLeft + Offset(art, art), Size(side - 2 * art, side - 2 * art))
        }

        // Month labels, centred on the first day of each month.
        chart.monthTicks.forEach { day ->
            // Three letters in every language: Spanish CLDR gives "sept", the mock shows SEP.
            val label = monthFormat.format(day).replace(".", "").take(3).uppercase(locale)
            val text = measurer.measure(label, monthStyle)
            val left = (x(day) - text.size.width / 2f).coerceIn(plotLeft - 4.dp.toPx(), size.width - text.size.width)
            drawText(text, topLeft = Offset(left, axisY + 6.dp.toPx()))
        }
    }
}

/** A horizontal line of [dash]-long segments with [gap] between them, [thickness] tall, centred on [y]. */
private fun DrawScope.dottedLine(color: Color, from: Float, to: Float, y: Float, thickness: Float, dash: Float, gap: Float) {
    var x = from
    while (x < to) {
        drawRect(color, Offset(x, y - thickness / 2), Size(min(dash, to - x), thickness))
        x += dash + gap
    }
}
