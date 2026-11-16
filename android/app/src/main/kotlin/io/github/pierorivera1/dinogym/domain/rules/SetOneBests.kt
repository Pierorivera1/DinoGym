package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.SetOneResult

/**
 * Progress records (decision 28). Progress counts only set 1, the freshest set,
 * and the record keeps only the days set 1 beat the previous best.
 *
 * "Better" is one comparison for both cases: compare the weight first (a missing
 * weight counts as 0 kg), then the reps. Without weight every weight is 0, so it
 * reduces to "more reps"; with weight it means "more weight, or the same weight
 * with more reps".
 */
object SetOneBests {

    /** True when [candidate] strictly beats [best]. */
    fun isBetter(candidate: SetOneResult, best: SetOneResult): Boolean {
        val candidateKg = candidate.weightKg ?: 0.0
        val bestKg = best.weightKg ?: 0.0
        return when {
            candidateKg != bestKg -> candidateKg > bestKg
            else -> candidate.reps > best.reps
        }
    }

    /**
     * From set-1 results in chronological order, the ones that beat every earlier
     * result. The first result is always kept: it is the first record.
     */
    fun bests(history: List<SetOneResult>): List<SetOneResult> {
        val records = mutableListOf<SetOneResult>()
        for (result in history) {
            val best = records.lastOrNull()
            if (best == null || isBetter(result, best)) records += result
        }
        return records
    }

    /**
     * Whether [candidate] earns the live "NEW BEST" tag against the earlier [history].
     * With no history there is nothing to beat, so the first session is never tagged.
     */
    fun isNewBest(candidate: SetOneResult, history: List<SetOneResult>): Boolean {
        val best = bests(history).lastOrNull() ?: return false
        return isBetter(candidate, best)
    }
}
