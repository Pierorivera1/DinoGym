package io.github.pierorivera1.dinogym.domain.model

/**
 * One workout. A null [endedAt] means it is still in progress.
 * [currentPosition] is the index into the routine's exercise list where the workout is,
 * so it can resume after the app is killed. Times are epoch milliseconds.
 */
data class Session(
    val id: String,
    val routineId: String,
    val programId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val currentPosition: Int,
) {
    val isInProgress: Boolean get() = endedAt == null
}

/**
 * One logged set. [setIndex] is 1-based, so "set 1" in the progress rules (decision 28)
 * is `setIndex == 1`. [weightKg] is null when no weight was logged.
 */
data class SetEntry(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val note: String?,
)

/** The numbers shown in the input fields for a set before the user changes them. */
data class SetValues(
    val reps: Int,
    val weightKg: Double?,
)

/** Set 1 of an exercise in one finished session, the unit progress is measured in. */
data class SetOneResult(
    val sessionId: String,
    val date: Long,
    val reps: Int,
    val weightKg: Double?,
)
