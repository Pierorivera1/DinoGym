package io.github.pierorivera1.dinogym.ui.stats

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

/** The y axis of the progress chart: its grid values, lowest first. */
data class YAxis(val ticks: List<Double>) {
    val min: Double get() = ticks.first()
    val max: Double get() = ticks.last()
}

/** The x axis: one equal slot per month, first record's month to the current month. */
data class XAxis(val months: List<YearMonth>, val labelEvery: Int)

/**
 * The scales of the progress chart (DESIGN.md "Chart"). Plain Kotlin, so it is unit-tested;
 * `ProgressChart` only draws what this computes.
 */
object ChartScale {

    /** Grid steps for reps (whole numbers) and for kilograms (plate steps of 2.5). */
    private val RepSteps = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)
    private val KgSteps = listOf(1.0, 2.5, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0)

    /** At most this many gaps between grid lines (five labels, as in the mock). */
    private const val MAX_INTERVALS = 4

    /** At most this many month labels; the rest are skipped so they never overlap. */
    private const val MAX_MONTH_LABELS = 6

    /**
     * Grid values for [values]: the smallest step that fits them in [MAX_INTERVALS] gaps, from
     * the step at or below the lowest value to the step above the highest, so the best never
     * touches the top (the mock: records 4–7, grid 4–8). [whole] is for reps; otherwise the
     * steps follow 2.5 kg plates.
     */
    fun yAxis(values: List<Double>, whole: Boolean): YAxis {
        if (values.isEmpty()) return YAxis(listOf(0.0, 1.0))
        val lowest = values.min()
        val highest = values.max()
        val steps = if (whole) RepSteps else KgSteps
        val step = steps.firstOrNull { intervals(lowest, highest, it) <= MAX_INTERVALS } ?: steps.last()
        val lo = floor(lowest / step) * step
        val count = intervals(lowest, highest, step)
        return YAxis((0..count).map { lo + it * step })
    }

    private fun intervals(lowest: Double, highest: Double, step: Double): Int {
        val lo = floor(lowest / step)
        val hi = floor(highest / step) + 1
        return (hi - lo).toInt()
    }

    /** Months from [first]'s month to [today]'s (or [last]'s, if later), labelled at most [MAX_MONTH_LABELS] times. */
    fun xAxis(first: LocalDate, last: LocalDate, today: LocalDate): XAxis {
        val start = YearMonth.from(first)
        val end = maxOf(YearMonth.from(last), YearMonth.from(today))
        val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.toList()
        return XAxis(months, labelEvery = ceil(months.size / MAX_MONTH_LABELS.toDouble()).toInt().coerceAtLeast(1))
    }

    /** Whether month [index] gets a label: counted back from the last, so the current month always has one. */
    fun isLabelled(axis: XAxis, index: Int): Boolean = (axis.months.lastIndex - index) % axis.labelEvery == 0

    /** Where [date] sits across the x axis, 0 to 1: its month's slot plus how far into the month it is. */
    fun xFraction(axis: XAxis, date: LocalDate): Double {
        val index = axis.months.indexOf(YearMonth.from(date)).coerceAtLeast(0)
        val inMonth = (date.dayOfMonth - 0.5) / date.lengthOfMonth()
        return (index + inMonth) / axis.months.size
    }

    /** Where [value] sits up the y axis, 0 (lowest grid line) to 1 (highest). */
    fun yFraction(axis: YAxis, value: Double): Double =
        if (axis.max == axis.min) 0.0 else (value - axis.min) / (axis.max - axis.min)
}
