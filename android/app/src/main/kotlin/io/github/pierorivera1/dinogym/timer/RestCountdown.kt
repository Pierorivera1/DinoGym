package io.github.pierorivera1.dinogym.timer

/**
 * A rest in progress. It is only an end time: nothing ticks in the background, and the
 * remaining time is always worked out from the clock, so it stays right with the screen off
 * or the app killed.
 *
 * @property endsAtMillis wall-clock time (epoch milliseconds) when the rest is over.
 * @property totalSeconds the length of the whole rest, including any −15s / +15s; the
 *   10-block bar compares the remaining time against it.
 * @property nextLabel what comes next, shown in the notification ("Pull-ups, set 2 of 2").
 */
data class RestTimerState(
    val endsAtMillis: Long,
    val totalSeconds: Int,
    val nextLabel: String,
)

/**
 * The countdown maths, kept free of Android so it can be unit-tested on the JVM.
 * Every function takes "now" as a parameter instead of reading a clock.
 */
object RestCountdown {
    /** The longest rest the picker offers: 9:00 (DESIGN.md "Rest time picker"). */
    const val MAX_SECONDS = 9 * 60

    /** What the −15s / +15s buttons change. */
    const val STEP_SECONDS = 15

    /** The rest screen's progress bar has 10 blocks. */
    const val BLOCKS = 10

    /**
     * A new rest of [seconds] starting at [nowMillis], clamped to 0:00–9:00.
     * Returns null for a rest of 0:00, which means "no rest".
     */
    fun start(seconds: Int, nextLabel: String, nowMillis: Long): RestTimerState? {
        val length = seconds.coerceIn(0, MAX_SECONDS)
        if (length == 0) return null
        return RestTimerState(nowMillis + length * 1000L, length, nextLabel)
    }

    /**
     * Whole seconds left, rounded up, so a 90 s rest shows 1:30 at the start and 0:01 in
     * its last second. Never negative.
     */
    fun remainingSeconds(endsAtMillis: Long, nowMillis: Long): Int {
        val leftMillis = endsAtMillis - nowMillis
        if (leftMillis <= 0) return 0
        return ((leftMillis + 999) / 1000).toInt()
    }

    /** True once the end time has been reached. */
    fun isExpired(state: RestTimerState, nowMillis: Long): Boolean = nowMillis >= state.endsAtMillis

    /**
     * Moves the end time by [deltaSeconds] (the screen passes ±[STEP_SECONDS]).
     * The remaining time stays between 0:00 and 9:00, and the total changes by the amount
     * actually applied, so the bar keeps meaning "remaining out of total".
     * Reaching 0:00 leaves an expired state: the rest is over now.
     */
    fun addSeconds(state: RestTimerState, deltaSeconds: Int, nowMillis: Long): RestTimerState {
        val remaining = remainingSeconds(state.endsAtMillis, nowMillis)
        val applied = (remaining + deltaSeconds).coerceIn(0, MAX_SECONDS) - remaining
        // An already expired rest restarts its count from now, not from the past end time.
        val base = maxOf(state.endsAtMillis, nowMillis)
        return state.copy(
            endsAtMillis = base + applied * 1000L,
            totalSeconds = (state.totalSeconds + applied).coerceAtLeast(0),
        )
    }

    /**
     * How many of the [BLOCKS] are filled: the share of the rest still to go, rounded to the
     * nearest block (1:48 left of 2:30 fills 7, as in the approved mock).
     */
    fun filledBlocks(totalSeconds: Int, remainingSeconds: Int): Int {
        if (totalSeconds <= 0) return 0
        val remaining = remainingSeconds.coerceIn(0, totalSeconds)
        // Integer rounding of remaining / total * BLOCKS.
        return (remaining * BLOCKS * 2 + totalSeconds) / (totalSeconds * 2)
    }
}
