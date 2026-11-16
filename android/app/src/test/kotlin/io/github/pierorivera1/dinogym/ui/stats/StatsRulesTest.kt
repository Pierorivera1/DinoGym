package io.github.pierorivera1.dinogym.ui.stats

import io.github.pierorivera1.dinogym.data.repository.SessionListItem
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import io.github.pierorivera1.dinogym.domain.rules.Gain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class StatsRulesTest {

    private val zone = ZoneId.of("America/Lima")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun item(id: String, start: String, minutes: Long, sets: Int = 10) = SessionListItem(
        Session(id, "r", "p", at(start), at(start) + minutes * 60_000, 0),
        routineName = "Pull",
        setCount = sets,
    )

    @Test
    fun historyGroupsByMonthNewestFirst() {
        val state = StatsHistoryMapper.map(
            sessions = listOf(
                item("oct", "2026-10-30T18:00", 40),
                item("nov2", "2026-11-16T07:00", 42),
                item("nov1", "2026-11-02T07:00", 30),
            ),
            newBests = mapOf("nov2" to setOf("a", "b")),
            zone = zone,
        )
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), state.months.map { it.month })
        assertEquals(listOf("nov2", "nov1"), state.months[0].rows.map { it.sessionId })
        assertEquals(2, state.months[0].rows[0].bestCount)
        assertEquals(0, state.months[0].rows[1].bestCount)
        assertEquals(42 * 60L, state.months[0].rows[0].durationSeconds)
        assertEquals(LocalDate.of(2026, 11, 16), state.months[0].rows[0].date)
    }

    @Test
    fun aLateWorkoutCountsInTheDevicesMonth() {
        // 23:30 on 31 October in Lima is already 1 November in UTC.
        val state = StatsHistoryMapper.map(listOf(item("late", "2026-10-31T23:30", 30)), emptyMap(), zone)
        assertEquals(YearMonth.of(2026, 10), state.months.single().month)
    }

    @Test
    fun noSessionsIsAnEmptyLoadedHistory() {
        val state = StatsHistoryMapper.map(emptyList(), emptyMap(), zone)
        assertFalse(state.isLoading)
        assertTrue(state.months.isEmpty())
    }

    @Test
    fun progressListLeavesOutShowOnlyAndShowsWeightOnlyAboveZero() {
        fun ex(id: String, kind: ExerciseKind = ExerciseKind.REPS) = Exercise(id, id, ExerciseIcon.GENERIC, kind, null, false, false)
        val state = ProgressListMapper.map(
            listOf(ex("dips"), ex("rope", ExerciseKind.SHOW_ONLY), ex("pulls"), ex("press")),
            mapOf(
                "dips" to SetOneResult("s", 1, 8, 20.0),
                "pulls" to SetOneResult("s", 1, 7, null),
                "press" to SetOneResult("s", 1, 9, 0.0),
            ),
        )
        assertEquals(listOf("dips", "pulls", "press"), state.rows.map { it.exerciseId })
        assertEquals(listOf(true, false, false), state.rows.map { it.usesWeight })
    }

    @Test
    fun yAxisLeavesRoomAboveTheBest() {
        assertEquals(listOf(4.0, 5.0, 6.0, 7.0, 8.0), ChartScale.yAxis(listOf(4.0, 5.0, 6.0, 7.0), whole = true).ticks)
        assertEquals(listOf(7.0, 8.0), ChartScale.yAxis(listOf(7.0), whole = true).ticks)
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), ChartScale.yAxis(listOf(5.0, 25.0), whole = true).ticks)
        // Kilograms may use 2.5 steps; reps never do.
        assertEquals(listOf(20.0, 22.5, 25.0, 27.5), ChartScale.yAxis(listOf(20.0, 22.5, 25.0), whole = false).ticks)
        assertEquals(listOf(10.0, 15.0, 20.0, 25.0), ChartScale.yAxis(listOf(10.0, 20.0), whole = true).ticks)
    }

    @Test
    fun xAxisRunsToTheCurrentMonthAndPlacesDaysInTheirSlot() {
        val axis = ChartScale.xAxis(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 9, 15), today = LocalDate.of(2026, 11, 16))
        assertEquals(5, axis.months.size)
        assertEquals(1, axis.labelEvery)
        // 16 November: the middle of the last of five slots.
        assertEquals(0.9, ChartScale.xFraction(axis, LocalDate.of(2026, 11, 16)), 0.01)
        assertEquals(0.0, ChartScale.xFraction(axis, LocalDate.of(2026, 7, 1)), 0.01)
    }

    @Test
    fun longXAxisLabelsAtMostSixMonthsIncludingTheCurrentOne() {
        val axis = ChartScale.xAxis(LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 1), today = LocalDate.of(2026, 11, 16))
        assertEquals(23, axis.months.size)
        val labelled = axis.months.indices.filter { ChartScale.isLabelled(axis, it) }
        assertTrue(labelled.size <= 6)
        assertTrue(axis.months.lastIndex in labelled)
    }

    @Test
    fun formatsResultsGainsAndDates() {
        val en = Locale.ENGLISH
        val es = Locale.forLanguageTag("es")
        assertEquals("7", StatsFormat.setOne(SetOneResult("s", 1, 7, null), usesWeight = false, en))
        assertEquals("22,5 kg × 8", StatsFormat.setOne(SetOneResult("s", 1, 8, 22.5), usesWeight = true, es))
        assertEquals("+3", StatsFormat.gain(Gain.Reps(3), en))
        assertEquals("+2.5 kg", StatsFormat.gain(Gain.Kg(2.5), en))
        assertEquals("0", StatsFormat.gain(Gain.Reps(0), en))
        assertEquals("NOVEMBER 2026", StatsFormat.monthHeading(YearMonth.of(2026, 11), en))
        assertEquals("NOVIEMBRE 2026", StatsFormat.monthHeading(YearMonth.of(2026, 11), es))
        val today = LocalDate.of(2026, 11, 16)
        assertEquals("Mon 16 Nov", StatsFormat.listDate(today, today, en))
        assertEquals("Mon 7 Jul 2025", StatsFormat.listDate(LocalDate.of(2025, 7, 7), today, en))
    }
}
