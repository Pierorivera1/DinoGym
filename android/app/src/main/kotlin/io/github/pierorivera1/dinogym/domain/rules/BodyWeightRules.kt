package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.WeighIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** The 7-day trailing average on a day that has at least one weigh-in. */
data class DailyAverage(val date: LocalDate, val averageKg: Double)

/** The chart range selector: 1M / 3M / ALL. */
enum class WeightRange(private val months: Long?) {
    ONE_MONTH(1),
    THREE_MONTHS(3),
    ALL(null),
    ;

    /** The first day shown when today is [today]; null for ALL (from the first weigh-in). */
    fun startDate(today: LocalDate): LocalDate? = months?.let { today.minusMonths(it) }
}

/** Which way the goal is from the current average (decision 32: inferred, never a setting). */
enum class GoalDirection { LOSE, GAIN, REACHED }

/** How far the current average is from the goal, and in which direction. */
data class GoalProgress(val direction: GoalDirection, val remainingKg: Double)

/**
 * Body weight rules (task 11, decision 32). Plain functions over weigh-ins, unit-tested.
 * Days are calendar days in the phone's time zone, passed in as [ZoneId].
 */
object BodyWeightRules {

    /** The days in the trailing window: the day itself and the 6 before it. */
    const val AVERAGE_DAYS = 7

    /** Accepted weights. Outside this a typo is far more likely than a real reading. */
    const val MIN_KG = 20.0
    const val MAX_KG = 400.0

    /** The step of the − and + buttons. */
    const val STEP_KG = 0.1

    /** Within this of the goal counts as reached (it shows as 0.0 kg to go). */
    private const val REACHED_KG = 0.05

    fun dayOf(measuredAt: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(measuredAt).atZone(zone).toLocalDate()

    /**
     * One [DailyAverage] per day that has a weigh-in, oldest first: the mean of every
     * weigh-in on that day and the 6 days before it. Several weigh-ins on one day all count.
     * The window looks back past any chart range, so the first point of a range is a full average.
     */
    fun trailingAverages(weighIns: List<WeighIn>, zone: ZoneId): List<DailyAverage> {
        val byDay = weighIns.groupBy { dayOf(it.measuredAt, zone) }.toSortedMap()
        val days = byDay.keys.toList()
        return days.map { day ->
            val from = day.minusDays((AVERAGE_DAYS - 1).toLong())
            val window = days.filter { it in from..day }.flatMap { byDay.getValue(it) }
            DailyAverage(day, window.sumOf { it.weightKg } / window.size)
        }
    }

    /** The average on the latest day with a weigh-in: the CURRENT panel. Null with no weigh-ins. */
    fun currentAverage(averages: List<DailyAverage>): Double? = averages.lastOrNull()?.averageKg

    /**
     * The CHANGE panel: the latest average minus the first average on or after [start] (all
     * of them when [start] is null). Null when the range holds fewer than two days.
     */
    fun change(averages: List<DailyAverage>, start: LocalDate?): Double? {
        val inRange = if (start == null) averages else averages.filter { !it.date.isBefore(start) }
        if (inRange.size < 2) return null
        return inRange.last().averageKg - inRange.first().averageKg
    }

    /** Where the goal is from [currentKg]: below means losing, above means gaining. */
    fun goalProgress(currentKg: Double, goalKg: Double): GoalProgress {
        val diff = goalKg - currentKg
        val direction = when {
            abs(diff) < REACHED_KG -> GoalDirection.REACHED
            diff < 0 -> GoalDirection.LOSE
            else -> GoalDirection.GAIN
        }
        return GoalProgress(direction, if (direction == GoalDirection.REACHED) 0.0 else roundTenth(abs(diff)))
    }

    /** True when [changeKg] moved toward the goal; it is coloured as progress. */
    fun isTowardGoal(changeKg: Double, direction: GoalDirection): Boolean = when (direction) {
        GoalDirection.LOSE -> changeKg < 0
        GoalDirection.GAIN -> changeKg > 0
        GoalDirection.REACHED -> false
    }

    /** Rounds to one decimal, the precision the app stores and shows. */
    fun roundTenth(kg: Double): Double = (kg * 10).roundToInt() / 10.0

    /**
     * Reads a typed weight: `78.3` or `78,3`, at most one decimal kept (rounded). Null when it is
     * not a number or is outside [MIN_KG]..[MAX_KG].
     */
    fun parseKg(text: String): Double? {
        val number = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (number.isNaN()) return null
        return roundTenth(number).takeIf { it in MIN_KG..MAX_KG }
    }

    /** One tap of − ([steps] = -1) or + (1): moves by [STEP_KG] and stays inside the accepted range. */
    fun nudge(kg: Double, steps: Int): Double = roundTenth(kg + steps * STEP_KG).coerceIn(MIN_KG, MAX_KG)
}
