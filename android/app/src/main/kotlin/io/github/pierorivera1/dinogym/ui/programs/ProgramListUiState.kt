package io.github.pierorivera1.dinogym.ui.programs

import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.data.repository.RoutineSummary
import io.github.pierorivera1.dinogym.data.seed.SeedText
import io.github.pierorivera1.dinogym.data.seed.StarterKey
import io.github.pierorivera1.dinogym.data.seed.StarterProgram
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.ui.home.CycleCell

/** Everything the Programs list shows. Built by [ProgramListMapper]. */
data class ProgramListUiState(
    val isLoading: Boolean = true,
    /** The active program first, then by name. */
    val programs: List<ProgramCardUi> = emptyList(),
    /** By name. */
    val routines: List<RoutineRowUi> = emptyList(),
    /** The `+ NEW` program sheet is open (decision 92). */
    val newProgramSheet: Boolean = false,
    /** The starter programs the sheet offers, in the phone's language. */
    val starters: List<StarterCardUi> = emptyList(),
)

/** One starter in the new-program sheet: "GYM · 5 DAYS", its size and its cycle. */
data class StarterCardUi(
    val key: StarterKey,
    val name: String,
    val routineCount: Int,
    val exerciseCount: Int,
    val cycle: List<CycleCell>,
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

    /** The sheet's cards, with names from [text] (SeedStrings in the app). No step is current. */
    fun starters(all: List<StarterProgram>, text: (SeedText) -> String): List<StarterCardUi> = all.map { starter ->
        StarterCardUi(
            key = starter.key,
            name = text(starter.program.name),
            routineCount = starter.program.routines.size,
            exerciseCount = starter.program.exercises.size,
            cycle = starter.program.cycle.map { CycleCell(it?.let(text), isCurrent = false) },
        )
    }
}
