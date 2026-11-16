package io.github.pierorivera1.dinogym.ui.programs

import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.data.repository.RoutineSummary
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.ui.home.CycleCell

/** Everything the Programs list shows. Built by [ProgramListMapper]. */
data class ProgramListUiState(
    val isLoading: Boolean = true,
    /** The active program first, then by name. */
    val programs: List<ProgramCardUi> = emptyList(),
    /** By name. */
    val routines: List<RoutineRowUi> = emptyList(),
)

/**
 * One program card: name, type, step count and, for the active program, the next step and
 * a cycle strip with that step marked. [nextRoutine] is null when the next step is a rest day
 * ([nextIsRest]) or the cycle is empty.
 */
data class ProgramCardUi(
    val id: String,
    val name: String,
    val type: ProgramType,
    val isActive: Boolean,
    val stepCount: Int,
    val nextRoutine: String?,
    val nextIsRest: Boolean,
    val cycle: List<CycleCell>,
)

/** One routine row: "PULL   6 exercises · 14 sets". */
data class RoutineRowUi(val id: String, val name: String, val exerciseCount: Int, val setCount: Int)

/** Turns the repositories' data into [ProgramListUiState]. A plain function, so it is unit-tested. */
object ProgramListMapper {
    fun map(programs: List<ProgramWithCycle>, routines: List<RoutineSummary>) = ProgramListUiState(
        isLoading = false,
        programs = programs.map { p ->
            val next = p.nextStep
            ProgramCardUi(
                id = p.program.id,
                name = p.program.name,
                type = p.program.type,
                isActive = p.program.isActive,
                stepCount = p.cycle.size,
                nextRoutine = next?.routineName,
                nextIsRest = next != null && next.step.isRestDay,
                // Only the active program marks a step: the others are not running.
                cycle = p.cycle.mapIndexed { i, step ->
                    CycleCell(step.routineName, isCurrent = p.program.isActive && i == p.nextIndex)
                },
            )
        },
        routines = routines.map { RoutineRowUi(it.routine.id, it.routine.name, it.exerciseCount, it.setCount) },
    )
}
