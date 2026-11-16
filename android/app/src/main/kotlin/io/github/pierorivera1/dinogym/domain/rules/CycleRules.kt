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

    /**
     * The stored step after the steps at [removed] (0-based indexes) leave a cycle of
     * [stepCount] steps. Steps removed before the current one shift it down, so the same step
     * stays next. When the current step itself is removed, the step after it becomes next,
     * and the first step when nothing comes after it. An emptied cycle returns 0.
     */
    fun afterRemoval(currentStep: Int, stepCount: Int, removed: Set<Int>): Int {
        val inRange = removed.filter { it in 0 until stepCount }
        val remaining = stepCount - inRange.size
        if (remaining <= 0) return 0
        val current = currentIndex(currentStep, stepCount)
        val shifted = current - inRange.count { it < current }
        return if (shifted >= remaining) 0 else shifted
    }

    /**
     * The stored step after a step is put back at [index] in a cycle that has [stepCount]
     * steps before it returns (UNDO of a removal). With [makeCurrent] (it was the next step
     * when it was removed) it is the next step again; otherwise the current step keeps
     * pointing at the same step, one place later when the new step goes before it.
     */
    fun afterInsert(currentStep: Int, stepCount: Int, index: Int, makeCurrent: Boolean): Int {
        if (makeCurrent || stepCount <= 0) return if (makeCurrent) index else 0
        val current = currentIndex(currentStep, stepCount)
        return if (current >= index) current + 1 else current
    }
}
