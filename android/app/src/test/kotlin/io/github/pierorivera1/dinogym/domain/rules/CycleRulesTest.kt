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

    // Editing the cycle (task 7)

    @Test
    fun removingAStepBeforeTheCurrentOneKeepsTheSameNextStep() {
        // Pull Push Legs Rest [Upper] Lower: removing Legs (2) keeps Upper next, now at 3.
        assertEquals(3, CycleRules.afterRemoval(currentStep = 4, stepCount = 6, removed = setOf(2)))
    }

    @Test
    fun removingAStepAfterTheCurrentOneChangesNothing() {
        assertEquals(1, CycleRules.afterRemoval(currentStep = 1, stepCount = 6, removed = setOf(4)))
    }

    @Test
    fun removingTheCurrentStepMakesTheFollowingStepNext() {
        assertEquals(2, CycleRules.afterRemoval(currentStep = 2, stepCount = 6, removed = setOf(2)))
    }

    @Test
    fun removingTheCurrentLastStepWrapsToTheFirst() {
        assertEquals(0, CycleRules.afterRemoval(currentStep = 5, stepCount = 6, removed = setOf(5)))
    }

    @Test
    fun removingSeveralStepsShiftsByThoseBeforeTheCurrentOne() {
        // A routine used twice in the cycle, before and after the current step.
        assertEquals(2, CycleRules.afterRemoval(currentStep = 3, stepCount = 6, removed = setOf(0, 5)))
    }

    @Test
    fun emptyingTheCycleReturnsZero() {
        assertEquals(0, CycleRules.afterRemoval(currentStep = 0, stepCount = 1, removed = setOf(0)))
    }

    @Test
    fun undoPutsTheNextStepBack() {
        // Step 2 was next and removed (next became the old step 3, now at 2); undo makes step 2 next again.
        assertEquals(2, CycleRules.afterInsert(currentStep = 2, stepCount = 5, index = 2, makeCurrent = true))
    }

    @Test
    fun undoBeforeTheCurrentStepShiftsItBack() {
        assertEquals(4, CycleRules.afterInsert(currentStep = 3, stepCount = 5, index = 2, makeCurrent = false))
    }

    @Test
    fun undoAfterTheCurrentStepChangesNothing() {
        assertEquals(1, CycleRules.afterInsert(currentStep = 1, stepCount = 5, index = 3, makeCurrent = false))
    }

    @Test
    fun removeThenUndoRestoresTheCurrentStep() {
        for (current in 0 until 6) {
            for (removed in 0 until 6) {
                val after = CycleRules.afterRemoval(current, 6, setOf(removed))
                val back = CycleRules.afterInsert(after, 5, removed, makeCurrent = removed == current)
                assertEquals("current $current, removed $removed", current, back)
            }
        }
    }
}
