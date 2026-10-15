package com.dinogym.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklyProgressTest {
    @Test fun groupsByLocalMondayAndKeepsGapsAndDeclines() {
        val zone = ZoneId.of("America/Lima")
        fun record(date: String, value: Int): Pair<Session, Entry> {
            val entry = Entry(starterExercises.first(), value, 2)
            return Session("Pull", listOf(entry), LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()) to entry
        }
        val points = weeklyProgress(listOf(record("2026-01-25", 12), record("2026-01-04", 10),
            record("2026-01-05", 15), record("2026-01-06", 11)), zone)
        assertEquals(listOf("2025-12-29", "2026-01-05", "2026-01-19"), points.map { it.week.toString() })
        assertEquals(listOf(10, 15, 12), points.map { it.value })
        assertTrue(weeklyProgress(emptyList(), zone).isEmpty())
    }
    @Test fun usesWorkoutIdAndSupportsLegacySessions() {
        val workout = starterWorkouts.first()
        val entry = Entry(starterExercises.first(), 10, 2)
        assertTrue(Session("Old name", listOf(entry), workoutId = workout.id).belongsTo(workout))
        assertTrue(Session(workout.name, listOf(entry)).belongsTo(workout))
        assertFalse(Session(workout.name, listOf(entry), workoutId = "other").belongsTo(workout))
    }
}
