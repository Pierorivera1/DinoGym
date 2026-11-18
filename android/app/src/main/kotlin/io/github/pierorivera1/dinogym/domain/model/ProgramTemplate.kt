package io.github.pierorivera1.dinogym.domain.model

/**
 * A program ready to be copied into the database as new rows: a starter program (task 22,
 * resolved to the phone's language) or, later, an imported share (task 23). Plain data with
 * no ids; [io.github.pierorivera1.dinogym.data.repository.ProgramInstaller] gives every row
 * a new id and reuses library exercises that already exist.
 *
 * [cycle] lists indexes into [routines]; null is a rest day.
 */
data class ProgramTemplate(
    val name: String,
    val type: ProgramType,
    val routines: List<RoutineTemplate>,
    val cycle: List<Int?>,
)

data class RoutineTemplate(
    val name: String,
    val items: List<RoutineItemTemplate>,
)

/** One line of a routine. Rest is in seconds; show-only exercises use 1 × 0 and no rest. */
data class RoutineItemTemplate(
    val exercise: ExerciseTemplate,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

/** An exercise as a template describes it. Matched against the library by name and kind. */
data class ExerciseTemplate(
    val name: String,
    val icon: ExerciseIcon,
    val kind: ExerciseKind,
    val template: String? = null,
    val weightEnabled: Boolean = false,
)
