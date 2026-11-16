package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.WeighIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class BodyWeightRulesTest {
    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 11, 1)

    private fun w(dayOffset: Long, kg: Double, hour: Int = 8, z: ZoneId = zone) =
        WeighIn("$dayOffset-$hour-$kg", day0.plusDays(dayOffset).atTime(hour, 0).atZone(z).toInstant().toEpochMilli(), kg)

    private fun avgs(vararg weighIns: WeighIn) = BodyWeightRules.trailingAverages(weighIns.toList(), zone)

    @Test
    fun averageIsTheMeanOfTheDayAndTheSixBefore() {
        val result = avgs(w(0, 80.0), w(3, 79.0), w(6, 78.0), w(7, 77.0))
        assertEquals(listOf(0L, 3L, 6L, 7L).map { day0.plusDays(it) }, result.map { it.date })
        assertEquals(80.0, result[0].averageKg, 1e-9)
        assertEquals(79.5, result[1].averageKg, 1e-9)
        assertEquals(79.0, result[2].averageKg, 1e-9)
        // Day 7: day 0 has left the window (days 1..7).
        assertEquals(78.0, result[3].averageKg, 1e-9)
    }

    @Test
    fun severalWeighInsOnOneDayAllCountAndGiveOnePoint() {
        val result = avgs(w(0, 80.0, hour = 7), w(0, 81.0, hour = 21), w(1, 79.0))
        assertEquals(2, result.size)
        assertEquals(80.5, result[0].averageKg, 1e-9)
        assertEquals(80.0, result[1].averageKg, 1e-9)
    }

    @Test
    fun inputOrderDoesNotMatter() {
        assertEquals(avgs(w(0, 80.0), w(2, 78.0)), avgs(w(2, 78.0), w(0, 80.0)))
    }

    @Test
    fun daysFollowTheGivenTimeZone() {
        // 23:30 in Lima is already the next day in UTC.
        val lima = ZoneId.of("America/Lima")
        val late = w(0, 80.0, hour = 23, z = lima)
        assertEquals(day0, BodyWeightRules.dayOf(late.measuredAt, lima))
        assertEquals(day0.plusDays(1), BodyWeightRules.dayOf(late.measuredAt, zone))
    }

    @Test
    fun noWeighInsGiveNoAverages() {
        assertTrue(avgs().isEmpty())
        assertNull(BodyWeightRules.currentAverage(emptyList()))
    }

    @Test
    fun currentIsTheLatestAverage() {
        assertEquals(79.0, BodyWeightRules.currentAverage(avgs(w(0, 80.0), w(1, 78.0)))!!, 1e-9)
    }

    @Test
    fun changeIsLatestMinusFirstAverageInRange() {
        val a = avgs(w(0, 82.0), w(10, 80.0), w(20, 79.0))
        assertEquals(-3.0, BodyWeightRules.change(a, start = null)!!, 1e-9)
        assertEquals(-1.0, BodyWeightRules.change(a, start = day0.plusDays(5))!!, 1e-9)
        // Only one day in range: no change to show.
        assertNull(BodyWeightRules.change(a, start = day0.plusDays(15)))
    }

    @Test
    fun rangeStartsGoBackFromToday() {
        val today = LocalDate.of(2026, 11, 16)
        assertEquals(LocalDate.of(2026, 10, 16), WeightRange.ONE_MONTH.startDate(today))
        assertEquals(LocalDate.of(2026, 8, 16), WeightRange.THREE_MONTHS.startDate(today))
        assertNull(WeightRange.ALL.startDate(today))
    }

    @Test
    fun goalDirectionIsInferredFromTheCurrentAverage() {
        assertEquals(GoalProgress(GoalDirection.LOSE, 2.4), BodyWeightRules.goalProgress(78.4, 76.0))
        assertEquals(GoalProgress(GoalDirection.GAIN, 1.5), BodyWeightRules.goalProgress(70.0, 71.5))
        assertEquals(GoalProgress(GoalDirection.REACHED, 0.0), BodyWeightRules.goalProgress(76.03, 76.0))
    }

    @Test
    fun progressIsAChangeTowardTheGoal() {
        assertTrue(BodyWeightRules.isTowardGoal(-0.5, GoalDirection.LOSE))
        assertFalse(BodyWeightRules.isTowardGoal(0.5, GoalDirection.LOSE))
        assertTrue(BodyWeightRules.isTowardGoal(0.5, GoalDirection.GAIN))
        assertFalse(BodyWeightRules.isTowardGoal(-0.5, GoalDirection.GAIN))
        assertFalse(BodyWeightRules.isTowardGoal(-0.5, GoalDirection.REACHED))
    }

    @Test
    fun parseAcceptsDotOrCommaAndRoundsToATenth() {
        assertEquals(78.3, BodyWeightRules.parseKg("78.3")!!, 0.0)
        assertEquals(78.3, BodyWeightRules.parseKg(" 78,3 ")!!, 0.0)
        assertEquals(78.4, BodyWeightRules.parseKg("78.35")!!, 0.0)
        assertEquals(80.0, BodyWeightRules.parseKg("80")!!, 0.0)
    }

    @Test
    fun parseRejectsNonsenseAndOutOfRange() {
        assertNull(BodyWeightRules.parseKg(""))
        assertNull(BodyWeightRules.parseKg("abc"))
        assertNull(BodyWeightRules.parseKg("7.8.3"))
        assertNull(BodyWeightRules.parseKg("19.9"))
        assertNull(BodyWeightRules.parseKg("400.1"))
        assertNull(BodyWeightRules.parseKg("NaN"))
    }

    @Test
    fun nudgeMovesByATenthWithoutDrift() {
        var kg = 78.0
        repeat(3) { kg = BodyWeightRules.nudge(kg, 1) }
        assertEquals(78.3, kg, 0.0)
        assertEquals(78.2, BodyWeightRules.nudge(78.3, -1), 0.0)
        assertEquals(BodyWeightRules.MIN_KG, BodyWeightRules.nudge(BodyWeightRules.MIN_KG, -1), 0.0)
    }
}
