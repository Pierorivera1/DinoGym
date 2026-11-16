package io.github.pierorivera1.dinogym.domain.rules

/**
 * Where a program is in its cycle (decision 29).
 *
 * The cycle advances by completed workout, not by weekday: finishing a workout,
 * marking a rest day done and skipping all move to the next step, and the last
 * step wraps around to the first.
 */
object CycleRules {

    /**
     * The step to show for a stored [currentStep] when the cycle has [stepCount] steps.
     *
     * A step past the end can happen when the user removes steps from the cycle; the
     * removed steps came after the end, so the next one is the start of the cycle (0).
     * An empty cycle also returns 0.
     */
    fun currentIndex(currentStep: Int, stepCount: Int): Int =
        if (stepCount <= 0 || currentStep !in 0 until stepCount) 0 else currentStep

    /** The step after [currentStep], wrapping at the end. An empty cycle stays at 0. */
    fun advance(currentStep: Int, stepCount: Int): Int {
        if (stepCount <= 0) return 0
        return (currentIndex(currentStep, stepCount) + 1) % stepCount
    }
}
