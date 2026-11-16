package io.github.pierorivera1.dinogym.ui.bodyweight

import io.github.pierorivera1.dinogym.domain.model.WeighIn
import io.github.pierorivera1.dinogym.domain.rules.GoalDirection
import io.github.pierorivera1.dinogym.domain.rules.WeightRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class BodyWeightStateMapperTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 16)

    private fun w(daysAgo: Long, kg: Double) =
        WeighIn("$daysAgo", today.minusDays(daysAgo).atTime(8, 0).toInstant(zone).toEpochMilli(), kg)

    /** Newest first, as the repository gives them. */
    private fun map(vararg weighIns: WeighIn, goal: Double? = null, range: WeightRange = WeightRange.THREE_MONTHS) =
        BodyWeightStateMapper.map(weighIns.sortedByDescending { it.measuredAt }, goal, range, today, zone)

    @Test
    fun noWeighInsIsTheEmptyState() {
        val state = map(goal = 76.0)
        assertFalse(state.isLoading)
        assertFalse(state.hasWeighIns)
        assertNull(state.currentKg)
        assertNull(state.chart)
        assertNull(state.goal)
    }

    @Test
    fun panelsShowAverageChangeAndGoal() {
        val state = map(w(60, 80.0), w(30, 79.0), w(0, 78.0), goal = 76.0)
        assertEquals(78.0, state.currentKg!!, 0.0)
        assertEquals(-2.0, state.changeKg!!, 1e-9)
        assertEquals(GoalDirection.LOSE, state.goal!!.direction)
        assertEquals(2.0, state.goal!!.remainingKg, 1e-9)
        assertTrue(state.changeTowardGoal)
    }

    @Test
    fun aChangeAwayFromTheGoalIsNotProgress() {
        val state = map(w(10, 77.0), w(0, 78.0), goal = 76.0)
        assertFalse(state.changeTowardGoal)
        assertFalse(map(w(10, 79.0), w(0, 78.0)).changeTowardGoal)
    }

    @Test
    fun theListIsNewestFirst() {
        val state = map(w(5, 79.0), w(0, 78.0), w(2, 78.5))
        assertEquals(listOf("0", "2", "5"), state.weighIns.map { it.id })
        assertEquals(today, state.weighIns.first().date)
    }

    @Test
    fun theChartKeepsOnlyTheRangeButAveragesLookBack() {
        val state = map(w(40, 90.0), w(20, 80.0), w(0, 78.0), range = WeightRange.ONE_MONTH)
        val chart = state.chart!!
        assertEquals(today.minusMonths(1), chart.startDay)
        assertEquals(today, chart.endDay)
        assertEquals(listOf(80.0, 78.0), chart.raw.map { it.kg })
        assertEquals(2, chart.average.size)
    }

    @Test
    fun aRangeWithNoWeighInHasNoChart() {
        val state = map(w(60, 80.0), range = WeightRange.ONE_MONTH)
        assertTrue(state.hasWeighIns)
        assertNull(state.chart)
    }

    @Test
    fun allStartsAtTheFirstWeighInAndShowsAtLeastAWeek() {
        assertEquals(today.minusDays(200), map(w(200, 80.0), w(0, 78.0), range = WeightRange.ALL).chart!!.startDay)
        assertEquals(today.minusDays(7), map(w(0, 78.0), range = WeightRange.ALL).chart!!.startDay)
    }

    @Test
    fun yAxisCoversDataAndGoalInWholeKg() {
        val chart = map(w(10, 80.6), w(0, 78.3), goal = 76.0).chart!!
        assertEquals(listOf(76, 77, 78, 79, 80, 81), chart.yTicks)
    }

    @Test
    fun yTicksStayFewAndSpanAtLeastTwoKg() {
        assertEquals(listOf(77, 78, 79), BodyWeightStateMapper.yTicks(78.2, 78.4))
        val wide = BodyWeightStateMapper.yTicks(60.0, 81.5)
        assertTrue(wide.size <= 6)
        assertTrue(wide.first() <= 60 && wide.last() >= 82)
        assertEquals(wide.zipWithNext { a, b -> b - a }.toSet().size, 1)
    }

    @Test
    fun monthTicksAreFirstDaysThinnedToSix() {
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
            BodyWeightStateMapper.monthTicks(LocalDate.of(2026, 8, 16), today),
        )
        assertTrue(BodyWeightStateMapper.monthTicks(LocalDate.of(2023, 1, 5), today).size <= 6)
    }

    @Test
    fun kgTextUsesTheLanguagesDecimalMark() {
        assertEquals("78.3", BodyWeightStateMapper.kgText(78.3, Locale.ENGLISH))
        assertEquals("78,3", BodyWeightStateMapper.kgText(78.3, Locale.forLanguageTag("es")))
        assertEquals("80.0", BodyWeightStateMapper.kgText(80.0, Locale.ENGLISH))
    }
}
