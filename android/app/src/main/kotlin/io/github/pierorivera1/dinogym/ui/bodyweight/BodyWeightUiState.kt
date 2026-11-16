package io.github.pierorivera1.dinogym.ui.bodyweight

import io.github.pierorivera1.dinogym.domain.model.WeighIn
import io.github.pierorivera1.dinogym.domain.rules.BodyWeightRules
import io.github.pierorivera1.dinogym.domain.rules.GoalProgress
import io.github.pierorivera1.dinogym.domain.rules.WeightRange
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Everything the WEIGHT segment of Stats shows. Built by [BodyWeightStateMapper]. */
data class BodyWeightUiState(
    val isLoading: Boolean = true,
    val range: WeightRange = WeightRange.THREE_MONTHS,
    /** False when no weigh-in exists at all: the empty state. */
    val hasWeighIns: Boolean = false,
    /** CURRENT: the latest 7-day average. */
    val currentKg: Double? = null,
    /** CHANGE over [range]; null with fewer than two days in it. */
    val changeKg: Double? = null,
    /** True when [changeKg] moved toward the goal, so it is coloured as progress. */
    val changeTowardGoal: Boolean = false,
    val goalKg: Double? = null,
    /** Direction and distance to the goal; null without a goal or a weigh-in. */
    val goal: GoalProgress? = null,
    /** Null when [range] holds no weigh-in. */
    val chart: WeightChart? = null,
    /** Every weigh-in, newest first. */
    val weighIns: List<WeighInRow> = emptyList(),
    /** The open sheet, if any. */
    val sheet: WeightSheet? = null,
    /** The weigh-in just deleted, while its UNDO bar shows. */
    val undo: WeighInRow? = null,
)

data class WeighInRow(val id: String, val date: LocalDate, val weightKg: Double)

/** The two sheets. Their text is what the KG field shows; it is parsed on save. */
sealed interface WeightSheet {
    val text: String

    /** Whether SAVE was tapped with a text that is not a valid weight. */
    val showError: Boolean

    /** LOG WEIGHT: a weight for [day], which steps back from [today] but never past it. */
    data class Log(val day: LocalDate, val today: LocalDate, override val text: String, override val showError: Boolean = false) :
        WeightSheet {
        val canGoForward: Boolean get() = day.isBefore(today)
    }

    /** GOAL WEIGHT: set or change the goal; CLEAR shows when one is set. */
    data class Goal(override val text: String, val hasGoal: Boolean, override val showError: Boolean = false) : WeightSheet
}

/**
 * What the chart draws, already cut to the range. The x axis runs from [startDay] to
 * [endDay] by calendar day; the y axis from the first to the last of [yTicks] (whole kg).
 */
data class WeightChart(
    val startDay: LocalDate,
    val endDay: LocalDate,
    val yTicks: List<Int>,
    /** Raw weigh-ins, small points. */
    val raw: List<ChartPoint>,
    /** The 7-day average, one point per day with a weigh-in: the main line. */
    val average: List<ChartPoint>,
    val goalKg: Double?,
    /** The first day of each month to label under the axis. */
    val monthTicks: List<LocalDate>,
) {
    val minKg: Int get() = yTicks.first()
    val maxKg: Int get() = yTicks.last()
}

data class ChartPoint(val date: LocalDate, val kg: Double)

/** Turns weigh-ins, the goal and the range into [BodyWeightUiState]. A plain function, unit-tested. */
object BodyWeightStateMapper {

    private const val MAX_Y_LABELS = 6
    private const val MAX_MONTH_LABELS = 6

    /** [weighIns] newest first, as the repository gives them. */
    fun map(
        weighIns: List<WeighIn>,
        goalKg: Double?,
        range: WeightRange,
        today: LocalDate,
        zone: ZoneId,
    ): BodyWeightUiState {
        val averages = BodyWeightRules.trailingAverages(weighIns, zone)
        val current = BodyWeightRules.currentAverage(averages)
        val change = BodyWeightRules.change(averages, range.startDate(today))
        val goal = if (goalKg != null && current != null) BodyWeightRules.goalProgress(current, goalKg) else null
        return BodyWeightUiState(
            isLoading = false,
            range = range,
            hasWeighIns = weighIns.isNotEmpty(),
            currentKg = current?.let(BodyWeightRules::roundTenth),
            changeKg = change?.let(BodyWeightRules::roundTenth),
            changeTowardGoal = change != null && goal != null && BodyWeightRules.isTowardGoal(change, goal.direction),
            goalKg = goalKg,
            goal = goal,
            chart = chart(weighIns, averages.map { ChartPoint(it.date, it.averageKg) }, goalKg, range, today, zone),
            weighIns = weighIns.map { WeighInRow(it.id, BodyWeightRules.dayOf(it.measuredAt, zone), it.weightKg) },
        )
    }

    private fun chart(
        weighIns: List<WeighIn>,
        averages: List<ChartPoint>,
        goalKg: Double?,
        range: WeightRange,
        today: LocalDate,
        zone: ZoneId,
    ): WeightChart? {
        val raw = weighIns.map { ChartPoint(BodyWeightRules.dayOf(it.measuredAt, zone), it.weightKg) }.sortedBy { it.date }
        val firstDay = raw.firstOrNull()?.date ?: return null
        val start = range.startDate(today) ?: firstDay
        val rawInRange = raw.filter { !it.date.isBefore(start) }
        if (rawInRange.isEmpty()) return null
        val averageInRange = averages.filter { !it.date.isBefore(start) }
        // A single day would give the x axis no width: show at least a week.
        val end = maxOf(today, rawInRange.last().date)
        val chartStart = if (start.isAfter(end.minusDays(7))) end.minusDays(7) else start

        val values = rawInRange.map { it.kg } + averageInRange.map { it.kg } + listOfNotNull(goalKg)
        return WeightChart(
            startDay = chartStart,
            endDay = end,
            yTicks = yTicks(values.min(), values.max()),
            raw = rawInRange,
            average = averageInRange,
            goalKg = goalKg,
            monthTicks = monthTicks(chartStart, end),
        )
    }

    /** Whole-kg labels covering [low]..[high], at most [MAX_Y_LABELS], spanning at least 2 kg. */
    internal fun yTicks(low: Double, high: Double): List<Int> {
        var bottom = floor(low).toInt()
        var top = ceil(high).toInt()
        if (top - bottom < 2) {
            val mid = (bottom + top) / 2.0
            bottom = floor(mid - 1).toInt()
            top = bottom + 2
        }
        // The smallest step (1, 2, 3 kg...) that needs no more than MAX_Y_LABELS labels once the
        // ends are moved out to multiples of it.
        var step = 1
        while (true) {
            val b = Math.floorDiv(bottom, step) * step
            val t = b + ceilDiv(top - b, step) * step
            if ((t - b) / step + 1 <= MAX_Y_LABELS) return (b..t step step).toList()
            step++
        }
    }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

    /** The first day of every month after [start] up to [end]; thinned to at most [MAX_MONTH_LABELS]. */
    internal fun monthTicks(start: LocalDate, end: LocalDate): List<LocalDate> {
        val all = generateSequence(start.withDayOfMonth(1).plusMonths(1)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(end) }
            .toList()
        val every = max(1, ceilDiv(all.size, MAX_MONTH_LABELS))
        return all.filterIndexed { index, _ -> index % every == 0 }
    }

    /** The kg field's text: one decimal, with the language's decimal mark (`78.3`, `78,3`). */
    fun kgText(kg: Double, locale: Locale): String =
        DecimalFormat("0.0", DecimalFormatSymbols.getInstance(locale)).format(kg)

    /** Where the log sheet starts when there is no weigh-in yet. */
    const val DEFAULT_START_KG = 70.0
}
