package io.github.pierorivera1.dinogym.ui.programs

import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.data.repository.RoutineSummary
import io.github.pierorivera1.dinogym.domain.model.ProgramType

/** Everything the program editor shows. Built by [ProgramEditMapper]. */
data class ProgramEditUiState(
    val isLoading: Boolean = true,
    /** The program does not exist (or was deleted): the screen says so. */
    val isMissing: Boolean = false,
    /** The name as typed; a blank name is shown as an error and not saved. */
    val name: String = "",
    val type: ProgramType = ProgramType.CALISTHENICS,
    val isActive: Boolean = false,
    val steps: List<CycleStepUi> = emptyList(),
    /** Every routine, for the `+ ROUTINE` sheet. */
    val routines: List<RoutineChoice> = emptyList(),
    val sheet: ProgramSheet? = null,
    /** The step just removed, while UNDO is offered. */
    val undo: RemovedStep? = null,
) {
    val nameError: Boolean get() = name.isBlank()
}

/** One cycle step. [routineName] and [routineId] are null for a rest day; [isNext] marks the NEXT tag. */
data class CycleStepUi(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val exerciseCount: Int,
    val isNext: Boolean,
)

/** A routine in the `+ ROUTINE` sheet. */
data class RoutineChoice(val id: String, val name: String, val exerciseCount: Int)

/** The sheets the program editor draws over itself. */
sealed interface ProgramSheet {
    data object AddRoutine : ProgramSheet
    data object ConfirmDelete : ProgramSheet
}

/**
 * What UNDO needs to put a removed step back: where it was and whether it was the next step.
 * [label] is its routine name, or null for a rest day.
 */
data class RemovedStep(val stepId: String, val position: Int, val wasNext: Boolean, val label: String?)

/** Builds [ProgramEditUiState]. A plain function, so it is unit-tested. */
object ProgramEditMapper {
    fun map(
        program: ProgramWithCycle?,
        nameDraft: String?,
        routines: List<RoutineSummary>,
        sheet: ProgramSheet?,
        undo: RemovedStep?,
    ): ProgramEditUiState {
        if (program == null) return ProgramEditUiState(isLoading = false, isMissing = true)
        val counts = routines.associate { it.routine.id to it.exerciseCount }
        return ProgramEditUiState(
            isLoading = false,
            name = nameDraft ?: program.program.name,
            type = program.program.type,
            isActive = program.program.isActive,
            steps = program.cycle.mapIndexed { i, info ->
                CycleStepUi(
                    id = info.step.id,
                    routineId = info.step.routineId,
                    routineName = info.routineName,
                    exerciseCount = info.step.routineId?.let { counts[it] } ?: 0,
                    isNext = i == program.nextIndex,
                )
            },
            routines = routines.map { RoutineChoice(it.routine.id, it.routine.name, it.exerciseCount) },
            sheet = sheet,
            undo = undo,
        )
    }
}
