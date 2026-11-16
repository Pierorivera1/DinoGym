package io.github.pierorivera1.dinogym.ui.workout

import io.github.pierorivera1.dinogym.timer.RestCountdown

/** Where the workout goes after a set is logged or an exercise is skipped. */
data class NextMove(
    /** The exercise position to be on next. */
    val position: Int,
    /** True when that position is past the last exercise: the workout is finished. */
    val finishes: Boolean,
)

/**
 * The small decisions of the workout flow, kept free of Android so they are unit-tested.
 */
object WorkoutRules {

    /** The rest picker's seconds wheel: 0, 5, … 55. */
    val SECOND_STEPS: List<Int> = (0..55 step 5).toList()

    /** The rest picker's minutes wheel: 0 … 9. */
    val MINUTE_STEPS: List<Int> = (0..9).toList()

    /**
     * After logging a set. [setsDone] counts the set just logged. Once the exercise has its
     * [targetSets], the workout moves to the next exercise; after the last one it finishes.
     */
    fun afterLog(setsDone: Int, targetSets: Int, position: Int, exerciseCount: Int): NextMove {
        val next = if (setsDone >= targetSets) position + 1 else position
        return NextMove(next, finishes = next >= exerciseCount)
    }

    /** SKIP, or DONE on a show-only exercise: go to the next exercise, or finish after the last. */
    fun moveOn(position: Int, exerciseCount: Int): NextMove =
        NextMove(position + 1, finishes = position + 1 >= exerciseCount)

    /**
     * The set the rest follows, for "set 1 of pull-ups done": the last set of the exercise
     * the workout is on, or, if it has none yet, the last set of the exercise before it.
     * [loggedCounts] holds the number of logged sets per exercise position.
     * Returns (exercise position, set number), or null when nothing fits.
     */
    fun lastDoneSet(position: Int, loggedCounts: List<Int>): Pair<Int, Int>? {
        val here = loggedCounts.getOrElse(position) { 0 }
        if (here > 0) return position to here
        val before = loggedCounts.getOrElse(position - 1) { 0 }
        return if (position > 0 && before > 0) position - 1 to before else null
    }

    /** Splits a rest length into the picker's wheel indexes (minutes, seconds), rounding to 5 s. */
    fun pickerIndexes(restSeconds: Int): Pair<Int, Int> {
        val rounded = ((restSeconds.coerceIn(0, RestCountdown.MAX_SECONDS) + 2) / 5) * 5
        val clamped = rounded.coerceAtMost(RestCountdown.MAX_SECONDS)
        return clamped / 60 to (clamped % 60) / 5
    }

    /** The rest length the wheels show, capped at 9:00 (9 minutes and any seconds is 9:00). */
    fun pickerSeconds(minuteIndex: Int, secondIndex: Int): Int {
        val minutes = MINUTE_STEPS.getOrElse(minuteIndex) { 0 }
        val seconds = SECOND_STEPS.getOrElse(secondIndex) { 0 }
        return (minutes * 60 + seconds).coerceAtMost(RestCountdown.MAX_SECONDS)
    }
}
