package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.SetEntry
import io.github.pierorivera1.dinogym.domain.model.SetValues

/**
 * The numbers pre-filled for a set (decision 28: the app repeats the last session,
 * it never suggests a progression).
 *
 * The history comes from the last finished session of the *same routine* that has
 * this exercise, because one exercise can have different targets in two routines
 * (dips are 12 × 3 in Push but 11 × 2 in Upper).
 */
object PreFill {

    /**
     * Values for set [setIndex] (1-based).
     *
     * - [lastSessionSets]: this exercise's sets from that last session, any order.
     * - Set k of the last session is used when it exists; if that session had fewer
     *   sets, its last set is repeated.
     * - With no history, the reps are the routine's [targetRepMin] and the weight is empty.
     */
    fun valuesFor(setIndex: Int, lastSessionSets: List<SetEntry>, targetRepMin: Int): SetValues {
        val sorted = lastSessionSets.sortedBy { it.setIndex }
        val source = sorted.firstOrNull { it.setIndex == setIndex } ?: sorted.lastOrNull()
            ?: return SetValues(reps = targetRepMin, weightKg = null)
        return SetValues(reps = source.reps, weightKg = source.weightKg)
    }
}
