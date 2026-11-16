package io.github.pierorivera1.dinogym.ui.stats

import io.github.pierorivera1.dinogym.data.repository.SessionDetail
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the past-session screen shows. */
data class SessionDetailUiState(
    val isLoading: Boolean = true,
    /** The session was deleted (or never existed). */
    val isMissing: Boolean = false,
    val routineName: String = "",
    val date: LocalDate? = null,
    val durationSeconds: Long = 0,
    val setCount: Int = 0,
    /** One card per exercise, in routine order. */
    val exercises: List<SessionExercise> = emptyList(),
    /** The set whose stepper is open under its card, or null. */
    val editing: EditingSet? = null,
    /** The delete dialog is open. */
    val confirmingDelete: Boolean = false,
)

/** One exercise card: icon, name, an optional note line, and a chip per set. */
data class SessionExercise(
    val exerciseId: String,
    val name: String,
    val icon: ExerciseIcon,
    /** The pattern (`3s + {n} pulls + 3s`) of a pattern exercise, shown under the name. */
    val template: String?,
    /** Set 1 set a new best in this session: the chip gets a ▲ and the card says so. */
    val isNewBest: Boolean,
    val sets: List<SetChip>,
)

/** One set chip: `SET n` over the reps. */
data class SetChip(val setId: String, val setIndex: Int, val reps: Int, val weightKg: Double?)

/**
 * The set being changed. [reps] and [weightKg] are what the stepper shows, ahead of the
 * database. [weightKg] is null when the set was logged without weight: no KG stepper.
 */
data class EditingSet(
    val setId: String,
    val exerciseId: String,
    val setIndex: Int,
    /** How many sets the exercise has in this session, for `SET 2 OF 2`. */
    val setCount: Int,
    val reps: Int,
    val weightKg: Double?,
)

/** Builds [SessionDetailUiState]. A plain function, so it is unit-tested. */
object SessionDetailMapper {
    /** [newBests] are the exercise ids whose set 1 set a new best in this session. */
    fun map(detail: SessionDetail?, newBests: Set<String>, zone: ZoneId): SessionDetailUiState {
        if (detail == null) return SessionDetailUiState(isLoading = false, isMissing = true)
        val session = detail.session
        return SessionDetailUiState(
            isLoading = false,
            routineName = detail.routineName,
            date = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate(),
            durationSeconds = ((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0) / 1000,
            setCount = detail.exercises.sumOf { it.sets.size },
            exercises = detail.exercises.map { (exercise, sets) ->
                SessionExercise(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    icon = exercise.icon,
                    template = exercise.template.takeIf { exercise.kind == ExerciseKind.TEMPLATE && !it.isNullOrBlank() },
                    isNewBest = exercise.id in newBests,
                    sets = sets.map { SetChip(it.id, it.setIndex, it.reps, it.weightKg) },
                )
            },
        )
    }
}
