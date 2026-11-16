package io.github.pierorivera1.dinogym.domain.model

/** How an exercise is logged. */
enum class ExerciseKind {
    /** One number per set: the reps. */
    REPS,

    /** Reps inside a fixed pattern such as `3s + {n} pulls + 3s`; only `{n}` is logged. */
    TEMPLATE,

    /** Shown in the routine but never logged (jump rope at the end of Legs). */
    SHOW_ONLY,
    ;

    companion object {
        /** Unknown values (for example from a newer app version after sync) fall back to [REPS]. */
        fun fromKey(key: String): ExerciseKind = entries.firstOrNull { it.name == key } ?: REPS
    }
}

/**
 * Icon keys stored in the database. The UI maps each key to a pixel drawable,
 * so the domain layer never references Android resources (decision 30).
 */
enum class ExerciseIcon {
    PULL_UP,
    ROW,
    CURL,
    DIP,
    PUSH_UP,
    RAISE,
    SQUAT,
    LEVER,
    JUMP_ROPE,
    GENERIC,
    ;

    companion object {
        /** Unknown keys fall back to [GENERIC] instead of crashing. */
        fun fromKey(key: String): ExerciseIcon = entries.firstOrNull { it.name == key } ?: GENERIC
    }
}

/**
 * An exercise in the library. One row is shared by every routine that uses it,
 * so dips in Push and dips in Upper have one history.
 *
 * [template] is only used when [kind] is [ExerciseKind.TEMPLATE]; `{n}` marks the logged number.
 */
data class Exercise(
    val id: String,
    val name: String,
    val icon: ExerciseIcon,
    val kind: ExerciseKind,
    val template: String?,
    val weightEnabled: Boolean,
    val archived: Boolean,
)
