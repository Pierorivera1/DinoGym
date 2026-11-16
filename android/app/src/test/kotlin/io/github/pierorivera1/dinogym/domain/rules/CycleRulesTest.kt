package io.github.pierorivera1.dinogym.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class CycleRulesTest {

    @Test
    fun advanceMovesToTheNextStep() {
        assertEquals(1, CycleRules.advance(currentStep = 0, stepCount = 6))
        assertEquals(4, CycleRules.advance(currentStep = 3, stepCount = 6))
    }

    @Test
    fun advanceWrapsAtTheEndOfTheCycle() {
        assertEquals(0, CycleRules.advance(currentStep = 5, stepCount = 6))
    }

    @Test
    fun singleStepCycleStaysOnItsOnlyStep() {
        assertEquals(0, CycleRules.advance(currentStep = 0, stepCount = 1))
    }

    @Test
    fun emptyCycleStaysAtZero() {
        assertEquals(0, CycleRules.advance(currentStep = 0, stepCount = 0))
        assertEquals(0, CycleRules.advance(currentStep = 3, stepCount = 0))
        assertEquals(0, CycleRules.currentIndex(currentStep = 3, stepCount = 0))
    }

    @Test
    fun cycleShortenedBelowTheCurrentStepRestartsAtTheFirstStep() {
        // The program was on step 5 and the cycle now has 3 steps.
        assertEquals(0, CycleRules.currentIndex(currentStep = 5, stepCount = 3))
        assertEquals(1, CycleRules.advance(currentStep = 5, stepCount = 3))
    }

    @Test
    fun negativeStepIsTreatedAsTheFirstStep() {
        assertEquals(0, CycleRules.currentIndex(currentStep = -1, stepCount = 4))
        assertEquals(1, CycleRules.advance(currentStep = -1, stepCount = 4))
    }

    @Test
    fun currentIndexKeepsAValidStep() {
        assertEquals(2, CycleRules.currentIndex(currentStep = 2, stepCount = 6))
    }
}
