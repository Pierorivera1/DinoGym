package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetOneBestsTest {

    private fun result(day: Long, reps: Int, weightKg: Double? = null) =
        SetOneResult(sessionId = "s$day", date = day, reps = reps, weightKg = weightKg)

    @Test
    fun emptyHistoryHasNoBests() {
        assertEquals(emptyList<SetOneResult>(), SetOneBests.bests(emptyList()))
    }

    @Test
    fun withoutWeightKeepsOnlyDaysWithMoreReps() {
        val history = listOf(
            result(1, 10),
            result(2, 10), // equal, not a new best
            result(3, 11),
            result(4, 9),
            result(5, 12),
        )
        assertEquals(listOf(1L, 3L, 5L), SetOneBests.bests(history).map { it.date })
    }

    @Test
    fun withWeightMoreWeightBeatsMoreReps() {
        val history = listOf(
            result(1, 10, 20.0),
            result(2, 12, 20.0), // same weight, more reps
            result(3, 6, 22.5), // more weight, fewer reps
            result(4, 11, 20.0), // less weight
            result(5, 6, 22.5), // equal
            result(6, 7, 22.5),
        )
        assertEquals(listOf(1L, 2L, 3L, 6L), SetOneBests.bests(history).map { it.date })
    }

    @Test
    fun missingWeightCountsAsZero() {
        val history = listOf(
            result(1, 15, null),
            result(2, 5, 5.0), // weight added: beats any reps at 0 kg
            result(3, 20, null), // back to no weight: lower than 5 kg
        )
        assertEquals(listOf(1L, 2L), SetOneBests.bests(history).map { it.date })
        assertFalse(SetOneBests.isBetter(result(9, 10, 0.0), result(8, 10, null)))
    }

    @Test
    fun isNewBestComparesAgainstTheBestSoFar() {
        val history = listOf(result(1, 10), result(2, 12), result(3, 8))
        assertTrue(SetOneBests.isNewBest(result(4, 13), history))
        assertFalse(SetOneBests.isNewBest(result(4, 12), history))
        assertFalse(SetOneBests.isNewBest(result(4, 11), history))
    }

    @Test
    fun firstSessionIsNotANewBest() {
        assertFalse(SetOneBests.isNewBest(result(1, 10), emptyList()))
    }
}
