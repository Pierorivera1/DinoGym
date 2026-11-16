package io.github.pierorivera1.dinogym.domain.model

/** Gym programs show the weight field; calisthenics programs hide it (decision 26). */
enum class ProgramType { GYM, CALISTHENICS }

/**
 * An ordered cycle of routines and rest days (decision 29).
 *
 * [currentStep] is the 0-based index into the cycle of the step shown next on Home.
 * Only one program is active at a time.
 */
data class Program(
    val id: String,
    val name: String,
    val type: ProgramType,
    val currentStep: Int,
    val isActive: Boolean,
)

/**
 * One step of a program's cycle. [position] is 0-based and contiguous inside a program.
 * A null [routineId] is a rest day.
 */
data class CycleStep(
    val id: String,
    val programId: String,
    val position: Int,
    val routineId: String?,
) {
    val isRestDay: Boolean get() = routineId == null
}
