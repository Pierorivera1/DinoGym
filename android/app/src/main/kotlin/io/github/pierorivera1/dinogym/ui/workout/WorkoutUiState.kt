package io.github.pierorivera1.dinogym.ui.workout

import io.github.pierorivera1.dinogym.data.repository.ActiveWorkout
import io.github.pierorivera1.dinogym.data.repository.WorkoutExercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind

/** Everything the active exercise screen shows. Built by [WorkoutStateMapper]. */
data class WorkoutUiState(
    val isLoading: Boolean = true,
    val routineName: String = "",
    val elapsedSeconds: Long = 0,
    /** 0-based position of the exercise the workout is on. */
    val position: Int = 0,
    val exerciseCount: Int = 0,
    val exercise: ActiveExerciseUi? = null,
    /** The exercise after this one, or null when this is the last. */
    val next: NextExerciseUi? = null,
    /** Sets logged in the whole workout, for the end-workout sheet. */
    val loggedSetCount: Int = 0,
    val sheet: WorkoutSheet = WorkoutSheet.None,
)

/** The exercise being done. */
data class ActiveExerciseUi(
    val name: String,
    val icon: ExerciseIcon,
    val kind: ExerciseKind,
    /** Set for template exercises: `3s + {n} pulls + 3s`. */
    val template: String?,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    /** Last session's reps per set, for "last time 6 · 6". Empty with no history. */
    val lastTime: List<Int>,
    val doneSets: List<DoneSetUi>,
    /** 1-based number of the set being logged. */
    val nextSetIndex: Int,
    /** The reps in the stepper. */
    val reps: Int,
    /** The weight in the stepper, or null when the weight field is hidden (decision 26). */
    val weightKg: Double?,
    /** True when the stepper values come from the last session, false from the routine target. */
    val preFilledFromHistory: Boolean,
    /** A note typed for the set being logged. */
    val note: String,
)

/** A logged set of the current exercise. */
data class DoneSetUi(
    val id: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    /** Set 1 beat the previous best: shows `▲ NEW BEST`. */
    val isNewBest: Boolean,
)

/** The NEXT box at the bottom: `NEXT Reverse curl   2 × 25`. */
data class NextExerciseUi(val name: String, val sets: Int, val repMin: Int, val repMax: Int, val kind: ExerciseKind)

/** The sheet open over the active exercise, if any. */
sealed interface WorkoutSheet {
    data object None : WorkoutSheet

    /** The rest time picker, starting at [seconds]. */
    data class RestPicker(val seconds: Int) : WorkoutSheet

    /** The note for the set being logged. */
    data class Note(val text: String) : WorkoutSheet

    /** Correct or delete a logged set. */
    data class EditSet(val setId: String, val setIndex: Int, val reps: Int, val weightKg: Double?) : WorkoutSheet

    /** The ✕: save and finish, discard, or keep going. */
    data object End : WorkoutSheet
}

/** The stepper values the user changed for one set, before logging it. */
data class SetDraft(val exerciseId: String, val setIndex: Int, val reps: Int, val weightKg: Double?)

/** Builds [WorkoutUiState] from the repository's [ActiveWorkout]. A plain function, so it is unit-tested. */
object WorkoutStateMapper {

    fun map(
        workout: ActiveWorkout,
        nowMillis: Long,
        draft: SetDraft?,
        note: String,
        sheet: WorkoutSheet,
    ): WorkoutUiState {
        val position = workout.session.currentPosition
        val current = workout.currentExercise
        val next = workout.exercises.getOrNull(position + 1)
        return WorkoutUiState(
            isLoading = false,
            routineName = workout.routine.name,
            elapsedSeconds = ((nowMillis - workout.session.startedAt) / 1000).coerceAtLeast(0),
            position = position,
            exerciseCount = workout.exercises.size,
            exercise = current?.let { exerciseUi(workout, it, draft, note) },
            next = next?.let {
                NextExerciseUi(
                    name = it.exercise.name,
                    sets = it.routineExercise.sets,
                    repMin = it.routineExercise.repMin,
                    repMax = it.routineExercise.repMax,
                    kind = it.exercise.kind,
                )
            },
            loggedSetCount = workout.exercises.sumOf { it.loggedSets.size },
            sheet = sheet,
        )
    }

    private fun exerciseUi(workout: ActiveWorkout, ex: WorkoutExercise, draft: SetDraft?, note: String): ActiveExerciseUi {
        val nextSetIndex = ex.loggedSets.size + 1
        val values = currentValues(workout, ex, draft)
        return ActiveExerciseUi(
            name = ex.exercise.name,
            icon = ex.exercise.icon,
            kind = ex.exercise.kind,
            template = ex.exercise.template.takeIf { ex.exercise.kind == ExerciseKind.TEMPLATE },
            sets = ex.routineExercise.sets,
            repMin = ex.routineExercise.repMin,
            repMax = ex.routineExercise.repMax,
            restSeconds = ex.routineExercise.restSeconds,
            lastTime = ex.lastSessionSets.sortedBy { it.setIndex }.map { it.reps },
            doneSets = ex.loggedSets.map { set ->
                DoneSetUi(
                    id = set.id,
                    setIndex = set.setIndex,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    isNewBest = set.setIndex == 1 && ex.isNewBest(set.reps, set.weightKg),
                )
            },
            nextSetIndex = nextSetIndex,
            reps = values.reps,
            weightKg = values.weightKg,
            preFilledFromHistory = ex.lastSessionSets.isNotEmpty(),
            note = note,
        )
    }

    /**
     * The values in the steppers for the set being logged: the user's [draft] if it is for
     * this exercise and set, else the pre-fill. The weight is null when its field is hidden,
     * and 0 when shown with no history.
     */
    fun currentValues(workout: ActiveWorkout, ex: WorkoutExercise, draft: SetDraft?): SetDraft {
        val setIndex = ex.loggedSets.size + 1
        val showsWeight = workout.showsWeightField(ex)
        if (draft != null && draft.exerciseId == ex.exercise.id && draft.setIndex == setIndex) {
            return draft.copy(weightKg = if (showsWeight) draft.weightKg ?: 0.0 else null)
        }
        val preFill = ex.preFill(setIndex)
        return SetDraft(
            exerciseId = ex.exercise.id,
            setIndex = setIndex,
            reps = preFill.reps,
            weightKg = if (showsWeight) preFill.weightKg ?: 0.0 else null,
        )
    }
}
