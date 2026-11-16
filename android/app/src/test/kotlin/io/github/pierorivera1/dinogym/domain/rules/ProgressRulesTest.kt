package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRulesTest {

    private fun result(day: Long, reps: Int, weightKg: Double? = null, session: String = "s$day") =
        SetOneResult(sessionId = session, date = day, reps = reps, weightKg = weightKg)

    @Test
    fun noHistoryHasNoBestAndNoGain() {
        val progress = ProgressRules.progress(emptyList())
        assertNull(progress.best)
        assertEquals(Gain.Reps(0), progress.gain)
        assertTrue(progress.records.isEmpty())
        assertFalse(progress.usesWeight)
    }

    @Test
    fun withoutWeightGainIsRepsOverTheFirstRecord() {
        val progress = ProgressRules.progress(listOf(result(1, 4), result(2, 4), result(3, 5), result(4, 3), result(5, 7)))
        assertEquals(7, progress.best?.reps)
        assertEquals(Gain.Reps(3), progress.gain)
        assertEquals(listOf(1L, 3L, 5L), progress.records.map { it.date })
        assertFalse(progress.usesWeight)
    }

    @Test
    fun recordsNewestFirstMarkTheFirstAndEachGain() {
        val records = ProgressRules.progress(listOf(result(1, 4), result(2, 5), result(3, 7))).recordsNewestFirst
        assertEquals(listOf(3L, 2L, 1L), records.map { it.result.date })
        assertEquals(listOf(Gain.Reps(2), Gain.Reps(1), null), records.map { it.gain })
    }

    @Test
    fun withWeightMoreWeightIsAKgGainEvenWithFewerReps() {
        val progress = ProgressRules.progress(
            listOf(
                result(1, 8, 20.0),
                result(2, 10, 20.0), // same weight, more reps: a record
                result(3, 6, 22.5), // more weight, fewer reps: a record
                result(4, 9, 20.0), // less weight: not a record
            ),
        )
        assertTrue(progress.usesWeight)
        assertEquals(listOf(1L, 2L, 3L), progress.records.map { it.date })
        assertEquals(Gain.Kg(2.5), progress.gain)
        assertEquals(listOf(Gain.Kg(2.5), Gain.Reps(2), null), progress.recordsNewestFirst.map { it.gain })
    }

    @Test
    fun zeroKilogramsDoesNotCountAsWeight() {
        assertFalse(ProgressRules.progress(listOf(result(1, 8, 0.0), result(2, 9, 0.0))).usesWeight)
    }

    @Test
    fun newBestsBySessionSkipEachExercisesFirstRecord() {
        val byExercise = mapOf(
            "pull" to listOf(result(1, 4, session = "a"), result(2, 5, session = "b"), result(3, 5, session = "c")),
            "dip" to listOf(result(1, 8, session = "a"), result(3, 9, session = "c")),
        )
        assertEquals(
            mapOf("b" to setOf("pull"), "c" to setOf("dip")),
            ProgressRules.newBestsBySession(byExercise),
        )
    }
}
