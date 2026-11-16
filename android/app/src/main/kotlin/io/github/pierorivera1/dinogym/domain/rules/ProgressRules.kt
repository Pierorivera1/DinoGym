package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.SetOneResult

/**
 * How much a record improved: more weight, or (at the same weight) more reps.
 * A record that adds weight usually drops reps (double progression restarts at 6),
 * so the gain is the weight then, never the reps.
 */
sealed interface Gain {
    data class Reps(val reps: Int) : Gain
    data class Kg(val kg: Double) : Gain
}

/** One entry of the RECORDS list. [gain] is null for the first record (`FIRST`). */
data class ProgressRecord(val result: SetOneResult, val gain: Gain?)

/** Everything the progress view of one exercise shows, built from its set-1 history. */
data class ExerciseProgress(
    /** The current best (the last record), or null with no history. */
    val best: SetOneResult?,
    /** The last record against the first one. Reps(0) with one record or none. */
    val gain: Gain,
    /** Records, oldest first: the chart's points. */
    val records: List<SetOneResult>,
    /** True when any set 1 logged a weight above 0 kg; then results read `20 kg × 8`. */
    val usesWeight: Boolean,
) {
    /** The RECORDS list, newest first. */
    val recordsNewestFirst: List<ProgressRecord>
        get() = records.mapIndexed { i, r -> ProgressRecord(r, if (i == 0) null else ProgressRules.gain(records[i - 1], r)) }.reversed()
}

/**
 * The progress view (decision 28): only set 1 counts, and a record is a day set 1 beat the
 * previous best ([SetOneBests]). Plain Kotlin, unit-tested on the JVM.
 */
object ProgressRules {

    /** The gain of [to] over [from]: kilograms when the weight went up, otherwise reps. */
    fun gain(from: SetOneResult, to: SetOneResult): Gain {
        val kg = (to.weightKg ?: 0.0) - (from.weightKg ?: 0.0)
        return if (kg > 0.0) Gain.Kg(kg) else Gain.Reps(to.reps - from.reps)
    }

    /** Builds [ExerciseProgress] from every set 1 of one exercise, oldest first. */
    fun progress(history: List<SetOneResult>): ExerciseProgress {
        val records = SetOneBests.bests(history)
        val first = records.firstOrNull()
        val best = records.lastOrNull()
        return ExerciseProgress(
            best = best,
            gain = if (first != null && best != null) gain(first, best) else Gain.Reps(0),
            records = records,
            usesWeight = history.any { (it.weightKg ?: 0.0) > 0.0 },
        )
    }

    /**
     * Which exercises set a new best in each session, from every set 1 grouped by exercise
     * id (each list oldest first). The first record of an exercise is not a new best: there
     * was nothing to beat, as in the summary ([SetOneBests.isNewBest]).
     * Returns session id → exercise ids.
     */
    fun newBestsBySession(historyByExercise: Map<String, List<SetOneResult>>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        historyByExercise.forEach { (exerciseId, history) ->
            SetOneBests.bests(history).drop(1).forEach { record ->
                result.getOrPut(record.sessionId) { mutableSetOf() } += exerciseId
            }
        }
        return result
    }
}
