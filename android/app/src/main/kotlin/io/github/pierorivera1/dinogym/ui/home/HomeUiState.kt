package io.github.pierorivera1.dinogym.ui.home

import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.data.repository.RoutineDetail
import io.github.pierorivera1.dinogym.data.repository.SessionListItem
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything Home shows. Built by [HomeStateMapper]; the screen only formats it. */
data class HomeUiState(
    val today: LocalDate,
    val isLoading: Boolean = false,
    /** Null when no program is active: Home shows its empty state. */
    val program: HomeProgram? = null,
    val lastWorkout: LastWorkout? = null,
)

/** The active program, its cycle and what comes next. */
data class HomeProgram(
    val id: String,
    val name: String,
    /** 1-based number of the next step, for "MY PROGRAM · 1 OF 6". */
    val stepNumber: Int,
    val stepCount: Int,
    val cycle: List<CycleCell>,
    val next: HomeNext,
)

/** One step of the cycle strip. A null [routineName] is a rest day. */
data class CycleCell(val routineName: String?, val isCurrent: Boolean)

/** What the Up next card shows. */
sealed interface HomeNext {
    /** A routine to start, or to resume when [inProgress]. */
    data class Workout(
        val routineId: String,
        val routineName: String,
        val exercises: List<HomeExerciseRow>,
        val inProgress: Boolean,
        /** The set-1 result the text box mentions, or null with no history. */
        val lastSetOne: LastSetOne?,
    ) : HomeNext {
        /** Sets to log: show-only items are never logged, so they do not count. */
        val totalSets: Int get() = exercises.filter { it.kind != ExerciseKind.SHOW_ONLY }.sumOf { it.sets }
    }

    /** A rest day. [followingRoutine] is the routine after it, if any. */
    data class RestDay(val followingRoutine: String?) : HomeNext

    /** The program has no steps. */
    data object EmptyCycle : HomeNext
}

/** One line of the Up next card. [template] is set for template exercises. */
data class HomeExerciseRow(
    val name: String,
    val kind: ExerciseKind,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val template: String?,
)

/** "Last time you did 6 pull-ups on set 1." */
data class LastSetOne(val exerciseName: String, val reps: Int)

/** The last finished workout row. */
data class LastWorkout(val routineName: String, val date: LocalDate)

/**
 * Turns the repositories' data into [HomeUiState]. A plain function, so it is unit-tested.
 */
object HomeStateMapper {

    fun map(
        today: LocalDate,
        zone: ZoneId,
        program: ProgramWithCycle?,
        inProgress: Session?,
        lastFinished: SessionListItem?,
        routine: RoutineDetail?,
        lastSetOnes: Map<String, SetOneResult>,
    ): HomeUiState {
        val lastWorkout = lastFinished?.let {
            LastWorkout(it.routineName, Instant.ofEpochMilli(it.session.startedAt).atZone(zone).toLocalDate())
        }
        if (program == null) return HomeUiState(today = today, lastWorkout = lastWorkout)

        val nextIndex = program.nextIndex
        val cycle = program.cycle.mapIndexed { index, info -> CycleCell(info.routineName, index == nextIndex) }
        val step = program.nextStep
        val next: HomeNext = when {
            // A workout in progress wins: Home offers to resume it.
            routine != null && (inProgress != null || step?.step?.routineId == routine.routine.id) -> HomeNext.Workout(
                routineId = routine.routine.id,
                routineName = routine.routine.name,
                exercises = routine.items.map {
                    HomeExerciseRow(
                        name = it.exercise.name,
                        kind = it.exercise.kind,
                        sets = it.routineExercise.sets,
                        repMin = it.routineExercise.repMin,
                        repMax = it.routineExercise.repMax,
                        template = it.exercise.template,
                    )
                },
                inProgress = inProgress != null,
                lastSetOne = pickLastSetOne(routine, lastSetOnes),
            )
            step == null -> HomeNext.EmptyCycle
            step.step.isRestDay -> HomeNext.RestDay(
                followingRoutine = program.cycle.getOrNull((nextIndex + 1) % program.cycle.size)?.routineName,
            )
            // The routine of the next step is still loading (or was deleted).
            else -> HomeNext.EmptyCycle
        }
        return HomeUiState(
            today = today,
            program = HomeProgram(
                id = program.program.id,
                name = program.program.name,
                stepNumber = nextIndex + 1,
                stepCount = program.cycle.size,
                cycle = cycle,
                next = next,
            ),
            lastWorkout = lastWorkout,
        )
    }

    /**
     * The exercise the text box talks about: the first plain-reps exercise with a set-1
     * result (pull-ups on Pull day, since front lever is a template), else the first exercise
     * with any result. Null when the routine has no history yet.
     */
    fun pickLastSetOne(routine: RoutineDetail, lastSetOnes: Map<String, SetOneResult>): LastSetOne? {
        val withHistory = routine.items.filter { lastSetOnes.containsKey(it.exercise.id) }
        val pick = withHistory.firstOrNull { it.exercise.kind == ExerciseKind.REPS } ?: withHistory.firstOrNull()
            ?: return null
        return LastSetOne(pick.exercise.name, lastSetOnes.getValue(pick.exercise.id).reps)
    }
}
