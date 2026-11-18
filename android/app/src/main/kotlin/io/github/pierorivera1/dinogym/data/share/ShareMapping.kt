package io.github.pierorivera1.dinogym.data.share

import io.github.pierorivera1.dinogym.api.models.ShareSnapshot
import io.github.pierorivera1.dinogym.api.models.SharedCycleStep
import io.github.pierorivera1.dinogym.api.models.SharedExercise
import io.github.pierorivera1.dinogym.api.models.SharedProgram
import io.github.pierorivera1.dinogym.api.models.SharedRoutine
import io.github.pierorivera1.dinogym.api.models.SharedRoutineExercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate

/** What a share holds, as the installer's templates (decision 96). */
sealed interface SharedContent {
    data class Routine(val routine: RoutineTemplate) : SharedContent
    data class Program(val program: ProgramTemplate) : SharedContent
}

/**
 * Templates ↔ the contract's [ShareSnapshot]. Pure, so it is tested on the JVM. The phone's
 * templates are the same shape as a snapshot: a routine is a [RoutineTemplate], a program a
 * [ProgramTemplate] whose cycle points into its routines.
 */
object ShareMapping {
    /** The snapshot format this app writes and reads. */
    const val VERSION = 1

    // The contract's limits (api/openapi.yaml). Names and patterns are cut to fit; too many
    // routines or lines cannot be cut without changing the routine, so they are refused.
    const val MAX_ROUTINES = 14
    const val MAX_LINES = 30
    const val MAX_CYCLE = 60
    private const val MAX_NAME = 120
    private const val MAX_TEMPLATE = 200

    /** Why a routine or program cannot be shared as it is. */
    enum class Problem { EMPTY, TOO_BIG }

    fun check(routine: RoutineTemplate): Problem? =
        if (routine.items.size > MAX_LINES) Problem.TOO_BIG else null

    fun check(program: ProgramTemplate): Problem? = when {
        program.cycle.none { it != null } -> Problem.EMPTY
        program.cycle.size > MAX_CYCLE || program.routines.size > MAX_ROUTINES -> Problem.TOO_BIG
        else -> program.routines.firstNotNullOfOrNull(::check)
    }

    fun toSnapshot(routine: RoutineTemplate) = ShareSnapshot(
        version = VERSION,
        kind = ShareSnapshot.Kind.ROUTINE,
        routines = listOf(routine.toShared()),
    )

    fun toSnapshot(program: ProgramTemplate) = ShareSnapshot(
        version = VERSION,
        kind = ShareSnapshot.Kind.PROGRAM,
        routines = program.routines.map { it.toShared() },
        program = SharedProgram(
            name = program.name.fit(MAX_NAME),
            type = if (program.type == ProgramType.GYM) SharedProgram.Type.GYM else SharedProgram.Type.CALISTHENICS,
            cycle = program.cycle.map { SharedCycleStep(routine = it) },
        ),
    )

    /**
     * The templates in [snapshot], or null when this app cannot read it: a newer format, or
     * a program whose cycle points at a routine that is not there.
     */
    fun fromSnapshot(snapshot: ShareSnapshot): SharedContent? {
        if (snapshot.version != VERSION) return null
        val routines = snapshot.routines.map { it.toTemplate() }
        return when (snapshot.kind) {
            ShareSnapshot.Kind.ROUTINE -> routines.singleOrNull()?.let { SharedContent.Routine(it) }
            ShareSnapshot.Kind.PROGRAM -> {
                val program = snapshot.program ?: return null
                val cycle = program.cycle.map { it.routine }
                if (cycle.any { it != null && it !in routines.indices }) return null
                SharedContent.Program(
                    ProgramTemplate(
                        name = program.name,
                        type = if (program.type == SharedProgram.Type.GYM) ProgramType.GYM else ProgramType.CALISTHENICS,
                        routines = routines,
                        cycle = cycle,
                    ),
                )
            }
        }
    }

    private fun RoutineTemplate.toShared() = SharedRoutine(
        name = name.fit(MAX_NAME),
        exercises = items.map { item ->
            SharedRoutineExercise(
                exercise = SharedExercise(
                    name = item.exercise.name.fit(MAX_NAME),
                    iconKey = item.exercise.icon.name,
                    kind = when (item.exercise.kind) {
                        ExerciseKind.REPS -> SharedExercise.Kind.REPS
                        ExerciseKind.TEMPLATE -> SharedExercise.Kind.TEMPLATE
                        ExerciseKind.SHOW_ONLY -> SharedExercise.Kind.SHOW_ONLY
                    },
                    weightEnabled = item.exercise.weightEnabled,
                    template = item.exercise.template?.fit(MAX_TEMPLATE),
                ),
                sets = item.sets,
                repMin = item.repMin,
                repMax = item.repMax,
                restSeconds = item.restSeconds,
            )
        },
    )

    private fun SharedRoutine.toTemplate() = RoutineTemplate(
        name = name,
        items = exercises.map { line ->
            RoutineItemTemplate(
                exercise = ExerciseTemplate(
                    name = line.exercise.name,
                    icon = ExerciseIcon.fromKey(line.exercise.iconKey),
                    kind = ExerciseKind.fromKey(line.exercise.kind.value),
                    template = line.exercise.template,
                    weightEnabled = line.exercise.weightEnabled,
                ),
                sets = line.sets,
                repMin = line.repMin,
                repMax = line.repMax,
                restSeconds = line.restSeconds,
            )
        },
    )

    /** [this] trimmed, and cut to [max] characters (never mid surrogate pair), or "?" when blank. */
    private fun String.fit(max: Int): String {
        val t = trim().ifEmpty { "?" }
        if (t.length <= max) return t
        val end = if (t[max - 1].isHighSurrogate()) max - 1 else max
        return t.substring(0, end)
    }
}
