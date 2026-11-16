package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.SetEntry
import io.github.pierorivera1.dinogym.domain.model.SetValues
import org.junit.Assert.assertEquals
import org.junit.Test

class PreFillTest {

    private fun set(index: Int, reps: Int, weightKg: Double? = null) = SetEntry(
        id = "s$index",
        sessionId = "session",
        exerciseId = "dips",
        setIndex = index,
        reps = reps,
        weightKg = weightKg,
        note = null,
    )

    @Test
    fun noHistoryUsesTheRoutineTargetAndNoWeight() {
        assertEquals(SetValues(12, null), PreFill.valuesFor(1, emptyList(), targetRepMin = 12))
    }

    @Test
    fun setKComesFromSetKOfTheLastSession() {
        val last = listOf(set(1, 13), set(2, 11, 5.0), set(3, 10))
        assertEquals(SetValues(13, null), PreFill.valuesFor(1, last, targetRepMin = 12))
        assertEquals(SetValues(11, 5.0), PreFill.valuesFor(2, last, targetRepMin = 12))
        assertEquals(SetValues(10, null), PreFill.valuesFor(3, last, targetRepMin = 12))
    }

    @Test
    fun moreSetsThanLastTimeRepeatsTheLastSet() {
        val last = listOf(set(1, 13), set(2, 11))
        assertEquals(SetValues(11, null), PreFill.valuesFor(3, last, targetRepMin = 12))
        assertEquals(SetValues(11, null), PreFill.valuesFor(5, last, targetRepMin = 12))
    }

    @Test
    fun orderOfTheInputDoesNotMatter() {
        val last = listOf(set(2, 11), set(1, 13))
        assertEquals(SetValues(13, null), PreFill.valuesFor(1, last, targetRepMin = 12))
        assertEquals(SetValues(11, null), PreFill.valuesFor(4, last, targetRepMin = 12))
    }
}
