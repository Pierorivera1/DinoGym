package io.github.pierorivera1.dinogym.data.seed

import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.data.seed.SeedText as T

/** One starter program offered by `+ NEW` on the Programs tab (task 22, decision 92). */
data class StarterProgram(
    val key: StarterKey,
    val program: SeedProgram,
    /** Workouts in one 7-step cycle, shown as "5 days a week". */
    val daysPerWeek: Int,
)

/** Stable keys, so the screen and the ViewModel name a starter without holding its data. */
enum class StarterKey { GYM_5, CALISTHENICS_5, GYM_4, CALISTHENICS_4 }

/**
 * The starter programs from TRAINING.md (decision 27): UL/PPL over 5 days and Upper/Lower
 * over 4, for Gym and for Calisthenics. Every exercise and target comes from the tables in
 * TRAINING.md "Starter routines". Where a row offers a choice ("hack squat or back squat"),
 * the first option is used; the user can rename it.
 *
 * Exercises that are the same movement as one in the owner's program use the same
 * [SeedText] key, so their names match and [io.github.pierorivera1.dinogym.domain.rules.InstallRules.match]
 * reuses the existing library row and its history.
 *
 * Icons: the closest of the existing pixel icons; GENERIC where none fits (decision 92).
 */
object StarterPrograms {
    private const val COMPOUND = 150
    private const val ISOLATION = 90

    private fun gym(exercise: T, sets: Int, rest: Int) = SeedRoutineItem(exercise, sets, reps = 6, restSeconds = rest, repMax = 10)
    private fun range(exercise: T, sets: Int, min: Int, max: Int, rest: Int) =
        SeedRoutineItem(exercise, sets, reps = min, restSeconds = rest, repMax = max)

    // Gym (TRAINING.md "Starter routines: Gym"): every exercise 6–10 reps.

    private val gymExercises = listOf(
        SeedExercise(T.EXERCISE_INCLINE_DUMBBELL_PRESS, ExerciseIcon.PUSH_UP),
        SeedExercise(T.EXERCISE_CHEST_SUPPORTED_ROW, ExerciseIcon.ROW),
        SeedExercise(T.EXERCISE_LAT_PULLDOWN, ExerciseIcon.PULL_UP),
        SeedExercise(T.EXERCISE_CABLE_LATERAL_RAISE, ExerciseIcon.RAISE),
        SeedExercise(T.EXERCISE_OVERHEAD_TRICEPS_EXTENSION, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_HACK_SQUAT, ExerciseIcon.SQUAT),
        SeedExercise(T.EXERCISE_ROMANIAN_DEADLIFT, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_SEATED_LEG_CURL, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_LEG_EXTENSION, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_STANDING_CALF_RAISE, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_MACHINE_CHEST_PRESS, ExerciseIcon.PUSH_UP),
        SeedExercise(T.EXERCISE_SEATED_CABLE_FLY, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_LATERAL_RAISE, ExerciseIcon.RAISE),
        SeedExercise(T.EXERCISE_SKULLCRUSHER, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_PULL_UP, ExerciseIcon.PULL_UP),
        SeedExercise(T.EXERCISE_ONE_ARM_DUMBBELL_ROW, ExerciseIcon.ROW),
        SeedExercise(T.EXERCISE_REVERSE_PEC_DECK, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_PREACHER_CURL, ExerciseIcon.CURL),
        SeedExercise(T.EXERCISE_BULGARIAN_SPLIT_SQUAT, ExerciseIcon.SQUAT),
        SeedExercise(T.EXERCISE_LEG_PRESS, ExerciseIcon.SQUAT),
        SeedExercise(T.EXERCISE_BACK_EXTENSION, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_SEATED_CALF_RAISE, ExerciseIcon.GENERIC),
    )

    private val gymUpper = SeedRoutine(
        T.ROUTINE_UPPER,
        listOf(
            gym(T.EXERCISE_INCLINE_DUMBBELL_PRESS, 3, COMPOUND),
            gym(T.EXERCISE_CHEST_SUPPORTED_ROW, 3, COMPOUND),
            gym(T.EXERCISE_LAT_PULLDOWN, 2, COMPOUND),
            gym(T.EXERCISE_CABLE_LATERAL_RAISE, 2, ISOLATION),
            gym(T.EXERCISE_OVERHEAD_TRICEPS_EXTENSION, 2, ISOLATION),
        ),
    )
    private val gymLower = SeedRoutine(
        T.ROUTINE_LOWER,
        listOf(
            gym(T.EXERCISE_HACK_SQUAT, 3, COMPOUND),
            gym(T.EXERCISE_ROMANIAN_DEADLIFT, 3, COMPOUND),
            gym(T.EXERCISE_SEATED_LEG_CURL, 2, ISOLATION),
            gym(T.EXERCISE_LEG_EXTENSION, 2, ISOLATION),
            gym(T.EXERCISE_STANDING_CALF_RAISE, 3, ISOLATION),
        ),
    )
    private val gymPush = SeedRoutine(
        T.ROUTINE_PUSH,
        listOf(
            gym(T.EXERCISE_MACHINE_CHEST_PRESS, 3, COMPOUND),
            gym(T.EXERCISE_SEATED_CABLE_FLY, 2, ISOLATION),
            gym(T.EXERCISE_LATERAL_RAISE, 3, ISOLATION),
            gym(T.EXERCISE_SKULLCRUSHER, 2, ISOLATION),
        ),
    )
    private val gymPull = SeedRoutine(
        T.ROUTINE_PULL,
        listOf(
            gym(T.EXERCISE_PULL_UP, 3, COMPOUND),
            gym(T.EXERCISE_ONE_ARM_DUMBBELL_ROW, 3, COMPOUND),
            gym(T.EXERCISE_REVERSE_PEC_DECK, 2, ISOLATION),
            gym(T.EXERCISE_PREACHER_CURL, 2, ISOLATION),
        ),
    )
    private val gymLegs = SeedRoutine(
        T.ROUTINE_LEGS,
        listOf(
            gym(T.EXERCISE_BULGARIAN_SPLIT_SQUAT, 3, COMPOUND),
            gym(T.EXERCISE_LEG_PRESS, 3, COMPOUND),
            gym(T.EXERCISE_BACK_EXTENSION, 2, ISOLATION),
            gym(T.EXERCISE_SEATED_CALF_RAISE, 3, ISOLATION),
        ),
    )

    // Calisthenics (TRAINING.md "Starter routines: Calisthenics").

    private val calisthenicsExercises = listOf(
        SeedExercise(T.EXERCISE_NEUTRAL_GRIP_PULL_UP, ExerciseIcon.PULL_UP),
        SeedExercise(T.EXERCISE_DIP, ExerciseIcon.DIP),
        SeedExercise(T.EXERCISE_PIKE_PUSH_UP, ExerciseIcon.PUSH_UP),
        SeedExercise(T.EXERCISE_LATERAL_RAISE, ExerciseIcon.RAISE),
        SeedExercise(T.EXERCISE_BICEP_CURL, ExerciseIcon.CURL),
        SeedExercise(T.EXERCISE_BULGARIAN_SPLIT_SQUAT, ExerciseIcon.SQUAT),
        SeedExercise(T.EXERCISE_NORDIC_CURL_NEGATIVE, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_SINGLE_LEG_HIP_THRUST, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_SINGLE_LEG_CALF_RAISE, ExerciseIcon.GENERIC),
        SeedExercise(T.EXERCISE_PUSH_UP, ExerciseIcon.PUSH_UP),
        SeedExercise(T.EXERCISE_DIAMOND_PUSH_UP, ExerciseIcon.PUSH_UP),
        SeedExercise(T.EXERCISE_PULL_UP, ExerciseIcon.PULL_UP),
        SeedExercise(T.EXERCISE_INVERTED_ROW, ExerciseIcon.ROW),
        SeedExercise(T.EXERCISE_REVERSE_CURL, ExerciseIcon.CURL),
        SeedExercise(T.EXERCISE_SQUAT, ExerciseIcon.SQUAT),
        SeedExercise(T.EXERCISE_WALKING_LUNGE, ExerciseIcon.SQUAT),
        SeedExercise(T.EXERCISE_JUMP_ROPE, ExerciseIcon.JUMP_ROPE, ExerciseKind.SHOW_ONLY),
    )

    private val caliUpper = SeedRoutine(
        T.ROUTINE_UPPER,
        listOf(
            range(T.EXERCISE_NEUTRAL_GRIP_PULL_UP, 3, 5, 10, COMPOUND),
            range(T.EXERCISE_DIP, 3, 8, 12, COMPOUND),
            range(T.EXERCISE_PIKE_PUSH_UP, 2, 6, 12, COMPOUND),
            range(T.EXERCISE_LATERAL_RAISE, 2, 12, 20, ISOLATION),
            range(T.EXERCISE_BICEP_CURL, 2, 10, 15, ISOLATION),
        ),
    )
    private val caliLower = SeedRoutine(
        T.ROUTINE_LOWER,
        listOf(
            range(T.EXERCISE_BULGARIAN_SPLIT_SQUAT, 3, 10, 15, COMPOUND),
            range(T.EXERCISE_NORDIC_CURL_NEGATIVE, 3, 4, 8, COMPOUND),
            range(T.EXERCISE_SINGLE_LEG_HIP_THRUST, 3, 10, 20, ISOLATION),
            range(T.EXERCISE_SINGLE_LEG_CALF_RAISE, 3, 10, 20, ISOLATION),
        ),
    )
    private val caliPush = SeedRoutine(
        T.ROUTINE_PUSH,
        listOf(
            range(T.EXERCISE_DIP, 3, 8, 12, COMPOUND),
            range(T.EXERCISE_PUSH_UP, 3, 8, 15, COMPOUND),
            range(T.EXERCISE_LATERAL_RAISE, 3, 12, 20, ISOLATION),
            range(T.EXERCISE_DIAMOND_PUSH_UP, 2, 8, 15, ISOLATION),
        ),
    )
    private val caliPull = SeedRoutine(
        T.ROUTINE_PULL,
        listOf(
            range(T.EXERCISE_PULL_UP, 3, 5, 10, COMPOUND),
            range(T.EXERCISE_INVERTED_ROW, 3, 8, 15, COMPOUND),
            range(T.EXERCISE_REVERSE_CURL, 2, 12, 20, ISOLATION),
            range(T.EXERCISE_BICEP_CURL, 2, 10, 15, ISOLATION),
        ),
    )
    private val caliLegs = SeedRoutine(
        T.ROUTINE_LEGS,
        listOf(
            range(T.EXERCISE_SQUAT, 3, 15, 25, COMPOUND),
            range(T.EXERCISE_WALKING_LUNGE, 3, 12, 20, ISOLATION),
            range(T.EXERCISE_SINGLE_LEG_CALF_RAISE, 3, 10, 20, ISOLATION),
            // Shown, not logged: one placeholder set and no rest, as in the owner's program.
            SeedRoutineItem(T.EXERCISE_JUMP_ROPE, sets = 1, reps = 0, restSeconds = 0),
        ),
    )

    /** Upper, Lower, Rest, Push, Pull, Legs, Rest (TRAINING.md "Program layouts"). */
    private val ulPplCycle = listOf(T.ROUTINE_UPPER, T.ROUTINE_LOWER, null, T.ROUTINE_PUSH, T.ROUTINE_PULL, T.ROUTINE_LEGS, null)

    /** Upper, Lower, Rest, Upper, Lower, Rest, Rest. */
    private val upperLowerCycle = listOf(T.ROUTINE_UPPER, T.ROUTINE_LOWER, null, T.ROUTINE_UPPER, T.ROUTINE_LOWER, null, null)

    private fun only(all: List<SeedExercise>, routines: List<SeedRoutine>): List<SeedExercise> {
        val used = routines.flatMap { r -> r.items.map { it.exercise } }.toSet()
        return all.filter { it.name in used }
    }

    private fun program(name: T, type: ProgramType, exercises: List<SeedExercise>, routines: List<SeedRoutine>, cycle: List<T?>) =
        SeedProgram(name, type, only(exercises, routines), routines, cycle)

    val all: List<StarterProgram> = listOf(
        StarterProgram(
            StarterKey.GYM_5,
            program(T.STARTER_GYM_5, ProgramType.GYM, gymExercises, listOf(gymUpper, gymLower, gymPush, gymPull, gymLegs), ulPplCycle),
            daysPerWeek = 5,
        ),
        StarterProgram(
            StarterKey.CALISTHENICS_5,
            program(
                T.STARTER_CALISTHENICS_5,
                ProgramType.CALISTHENICS,
                calisthenicsExercises,
                listOf(caliUpper, caliLower, caliPush, caliPull, caliLegs),
                ulPplCycle,
            ),
            daysPerWeek = 5,
        ),
        StarterProgram(
            StarterKey.GYM_4,
            program(T.STARTER_GYM_4, ProgramType.GYM, gymExercises, listOf(gymUpper, gymLower), upperLowerCycle),
            daysPerWeek = 4,
        ),
        StarterProgram(
            StarterKey.CALISTHENICS_4,
            program(T.STARTER_CALISTHENICS_4, ProgramType.CALISTHENICS, calisthenicsExercises, listOf(caliUpper, caliLower), upperLowerCycle),
            daysPerWeek = 4,
        ),
    )

    fun get(key: StarterKey): StarterProgram = all.first { it.key == key }
}
