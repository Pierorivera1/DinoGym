package io.github.pierorivera1.dinogym.data.seed

import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ProgramType

/*
 * The data inserted on first launch, as plain Kotlin with no Android imports so it is
 * unit-tested on the JVM. Names are [SeedText] keys, resolved to English or Spanish
 * once at seed time (SeedStrings.kt); after that they are ordinary user data.
 */

/** Every name or template the seed needs, one string resource each in `strings_seed.xml`. */
enum class SeedText {
    PROGRAM_NAME,

    ROUTINE_PULL,
    ROUTINE_PUSH,
    ROUTINE_LEGS,
    ROUTINE_UPPER,
    ROUTINE_LOWER,

    EXERCISE_FRONT_LEVER,
    TEMPLATE_FRONT_LEVER,
    EXERCISE_FOREARM_CURL,
    EXERCISE_PULL_UP,
    EXERCISE_REVERSE_CURL,
    EXERCISE_ROW,
    EXERCISE_BICEP_CURL,
    EXERCISE_DIP,
    EXERCISE_SINGLE_ARM_LATERAL_RAISE,
    EXERCISE_HOLLOW_PUSH_UP,
    EXERCISE_LATERAL_RAISE,
    EXERCISE_TRICEPS_PUSH_UP,
    EXERCISE_BULGARIAN_SPLIT_SQUAT,
    EXERCISE_SQUAT,
    EXERCISE_JUMP_ROPE,
    EXERCISE_NEUTRAL_GRIP_PULL_UP,
    EXERCISE_PUSH_UP,
}

/** An exercise of the library. [name] identifies it, so an exercise used by two routines is one row. */
data class SeedExercise(
    val name: SeedText,
    val icon: ExerciseIcon,
    val kind: ExerciseKind = ExerciseKind.REPS,
    val template: SeedText? = null,
)

/** One line of a routine: "Dips 12 × 3" is `reps = 12, sets = 3`. */
data class SeedRoutineItem(
    val exercise: SeedText,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)

data class SeedRoutine(
    val name: SeedText,
    val items: List<SeedRoutineItem>,
)

/** A whole program. In [cycle], a routine name is a workout and null is a rest day. */
data class SeedProgram(
    val name: SeedText,
    val type: ProgramType,
    val exercises: List<SeedExercise>,
    val routines: List<SeedRoutine>,
    val cycle: List<SeedText?>,
)

/**
 * The owner's program from WISHLIST.md ("The owner's first program"), Calisthenics.
 *
 * Rest follows TRAINING.md: 2:30 after compound moves (pulls, rows, dips, push-ups,
 * squats, front lever), 1:30 after isolation moves (curls, raises). Jump rope is
 * show-only, so its rest is 0 and it has one placeholder set that is never logged.
 */
object OwnerProgramSeed {
    private const val COMPOUND = 150
    private const val ISOLATION = 90

    private val pullItems = listOf(
        SeedRoutineItem(SeedText.EXERCISE_FRONT_LEVER, sets = 2, reps = 4, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_FOREARM_CURL, sets = 3, reps = 13, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_PULL_UP, sets = 2, reps = 6, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_REVERSE_CURL, sets = 2, reps = 25, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_ROW, sets = 3, reps = 12, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_BICEP_CURL, sets = 2, reps = 12, restSeconds = ISOLATION),
    )

    private val pushItems = listOf(
        SeedRoutineItem(SeedText.EXERCISE_DIP, sets = 3, reps = 12, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_SINGLE_ARM_LATERAL_RAISE, sets = 2, reps = 10, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_HOLLOW_PUSH_UP, sets = 2, reps = 8, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_LATERAL_RAISE, sets = 2, reps = 14, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_TRICEPS_PUSH_UP, sets = 2, reps = 8, restSeconds = COMPOUND),
    )

    /** Legs and Lower have the same exercises but are separate routines. */
    private val legItems = listOf(
        SeedRoutineItem(SeedText.EXERCISE_BULGARIAN_SPLIT_SQUAT, sets = 3, reps = 12, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_SQUAT, sets = 2, reps = 15, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_JUMP_ROPE, sets = 1, reps = 0, restSeconds = 0),
    )

    private val upperItems = listOf(
        SeedRoutineItem(SeedText.EXERCISE_FRONT_LEVER, sets = 2, reps = 4, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_SINGLE_ARM_LATERAL_RAISE, sets = 2, reps = 10, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_DIP, sets = 2, reps = 11, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_NEUTRAL_GRIP_PULL_UP, sets = 3, reps = 7, restSeconds = COMPOUND),
        SeedRoutineItem(SeedText.EXERCISE_LATERAL_RAISE, sets = 2, reps = 10, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_REVERSE_CURL, sets = 2, reps = 12, restSeconds = ISOLATION),
        SeedRoutineItem(SeedText.EXERCISE_PUSH_UP, sets = 2, reps = 12, restSeconds = COMPOUND),
    )

    val program = SeedProgram(
        name = SeedText.PROGRAM_NAME,
        type = ProgramType.CALISTHENICS,
        exercises = listOf(
            SeedExercise(
                SeedText.EXERCISE_FRONT_LEVER,
                ExerciseIcon.LEVER,
                ExerciseKind.TEMPLATE,
                template = SeedText.TEMPLATE_FRONT_LEVER,
            ),
            SeedExercise(SeedText.EXERCISE_FOREARM_CURL, ExerciseIcon.CURL),
            SeedExercise(SeedText.EXERCISE_PULL_UP, ExerciseIcon.PULL_UP),
            SeedExercise(SeedText.EXERCISE_REVERSE_CURL, ExerciseIcon.CURL),
            SeedExercise(SeedText.EXERCISE_ROW, ExerciseIcon.ROW),
            SeedExercise(SeedText.EXERCISE_BICEP_CURL, ExerciseIcon.CURL),
            SeedExercise(SeedText.EXERCISE_DIP, ExerciseIcon.DIP),
            SeedExercise(SeedText.EXERCISE_SINGLE_ARM_LATERAL_RAISE, ExerciseIcon.RAISE),
            SeedExercise(SeedText.EXERCISE_HOLLOW_PUSH_UP, ExerciseIcon.PUSH_UP),
            SeedExercise(SeedText.EXERCISE_LATERAL_RAISE, ExerciseIcon.RAISE),
            SeedExercise(SeedText.EXERCISE_TRICEPS_PUSH_UP, ExerciseIcon.PUSH_UP),
            SeedExercise(SeedText.EXERCISE_BULGARIAN_SPLIT_SQUAT, ExerciseIcon.SQUAT),
            SeedExercise(SeedText.EXERCISE_SQUAT, ExerciseIcon.SQUAT),
            SeedExercise(SeedText.EXERCISE_JUMP_ROPE, ExerciseIcon.JUMP_ROPE, ExerciseKind.SHOW_ONLY),
            SeedExercise(SeedText.EXERCISE_NEUTRAL_GRIP_PULL_UP, ExerciseIcon.PULL_UP),
            SeedExercise(SeedText.EXERCISE_PUSH_UP, ExerciseIcon.PUSH_UP),
        ),
        routines = listOf(
            SeedRoutine(SeedText.ROUTINE_PULL, pullItems),
            SeedRoutine(SeedText.ROUTINE_PUSH, pushItems),
            SeedRoutine(SeedText.ROUTINE_LEGS, legItems),
            SeedRoutine(SeedText.ROUTINE_UPPER, upperItems),
            SeedRoutine(SeedText.ROUTINE_LOWER, legItems),
        ),
        cycle = listOf(
            SeedText.ROUTINE_PULL,
            SeedText.ROUTINE_PUSH,
            SeedText.ROUTINE_LEGS,
            null,
            SeedText.ROUTINE_UPPER,
            SeedText.ROUTINE_LOWER,
        ),
    )
}
