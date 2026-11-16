package io.github.pierorivera1.dinogym.domain.model

/** An ordered list of exercises with set targets (version 1 called it a workout). */
data class Routine(
    val id: String,
    val name: String,
)

/**
 * One exercise inside a routine, with its target. A fixed target such as "12 reps"
 * has [repMin] == [repMax]. [position] is 0-based and contiguous inside the routine.
 */
data class RoutineExercise(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
) {
    val isFixedTarget: Boolean get() = repMin == repMax
}
