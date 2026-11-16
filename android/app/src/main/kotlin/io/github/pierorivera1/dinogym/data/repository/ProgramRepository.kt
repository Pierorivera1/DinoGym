package io.github.pierorivera1.dinogym.data.repository

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.CycleStepEntity
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ProgramDao
import io.github.pierorivera1.dinogym.data.local.ProgramEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.domain.model.CycleStep
import io.github.pierorivera1.dinogym.domain.model.Program
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.rules.CycleRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** A cycle step with the name of its routine; [routineName] is null for a rest day. */
data class CycleStepInfo(
    val step: CycleStep,
    val routineName: String?,
)

/** A program and its cycle in order, with the step Home shows next. */
data class ProgramWithCycle(
    val program: Program,
    val cycle: List<CycleStepInfo>,
) {
    /** Index of the next step, already corrected for a shortened or empty cycle. */
    val nextIndex: Int get() = CycleRules.currentIndex(program.currentStep, cycle.size)

    /** The next step, or null when the cycle is empty. */
    val nextStep: CycleStepInfo? get() = cycle.getOrNull(nextIndex)
}

/**
 * Programs and their cycles: the active program for Home, and the Programs screen's
 * list and cycle editor. Writes stamp `updatedAt`; deletes are soft.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ProgramRepository @Inject constructor(
    private val db: DinoDatabase,
    private val programDao: ProgramDao,
    private val cycleWriter: CycleWriter,
    private val clock: Clock,
) {

    /** The active program with its cycle, or null when no program is active. */
    fun observeActiveProgram(): Flow<ProgramWithCycle?> =
        programDao.observeActive().flatMapLatest { program ->
            if (program == null) flowOf(null) else observeCycleOf(program.toDomain())
        }

    fun observeProgram(id: String): Flow<ProgramWithCycle?> =
        programDao.observeById(id).flatMapLatest { program ->
            if (program == null) flowOf(null) else observeCycleOf(program.toDomain())
        }

    fun observePrograms(): Flow<List<Program>> =
        programDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Every program with its cycle, for the Programs list: the active one first, then by name. */
    fun observeProgramsWithCycles(): Flow<List<ProgramWithCycle>> = combine(
        programDao.observeAll(),
        programDao.observeAllCycles(),
    ) { programs, steps ->
        val byProgram = steps.groupBy { it.programId }
        programs
            .map { row ->
                ProgramWithCycle(
                    row.toDomain(),
                    byProgram[row.id].orEmpty().map { CycleStepInfo(it.toDomain(), it.routineName) },
                )
            }
            .sortedByDescending { it.program.isActive } // stable: keeps the name order inside each group
    }

    /** Names of the programs whose cycle uses [routineId], for the routine's delete dialog. */
    fun observeProgramNamesUsingRoutine(routineId: String): Flow<List<String>> =
        programDao.observeProgramNamesUsingRoutine(routineId)

    private fun observeCycleOf(program: Program): Flow<ProgramWithCycle> =
        programDao.observeCycle(program.id).map { rows ->
            ProgramWithCycle(program, rows.map { CycleStepInfo(it.toDomain(), it.routineName) })
        }

    /**
     * Creates an empty program and returns its id. It is inactive, unless [activateIfNone] is
     * set and no program is active: then it becomes the active one, so Home has a program.
     */
    suspend fun createProgram(name: String, type: ProgramType, activateIfNone: Boolean = false): String =
        db.withWriteTransaction {
            val id = newId()
            val activate = activateIfNone && programDao.getActive() == null
            programDao.insert(
                ProgramEntity(
                    id = id,
                    name = name,
                    type = type.name,
                    currentStep = 0,
                    isActive = activate,
                    updatedAt = clock.millis(),
                ),
            )
            id
        }

    suspend fun setProgramType(id: String, type: ProgramType) = programDao.setType(id, type.name, clock.millis())

    suspend fun renameProgram(id: String, name: String) = programDao.rename(id, name, clock.millis())

    /** Soft-deletes the program and its cycle. Its finished sessions stay in history. */
    suspend fun deleteProgram(id: String) = db.withWriteTransaction {
        val now = clock.millis()
        programDao.softDeleteStepsOfProgram(id, now)
        programDao.softDelete(id, now)
    }

    /** Makes [id] the only active program. */
    suspend fun setActiveProgram(id: String) = db.withWriteTransaction {
        val now = clock.millis()
        programDao.clearActive(now)
        programDao.setActive(id, now)
    }

    /** Appends a step to the end of the cycle; a null [routineId] adds a rest day. Returns the step id. */
    suspend fun addCycleStep(programId: String, routineId: String?): String = db.withWriteTransaction {
        val id = newId()
        val position = programDao.getCycle(programId).size
        programDao.insertSteps(
            listOf(
                CycleStepEntity(
                    id = id,
                    programId = programId,
                    position = position,
                    routineId = routineId,
                    updatedAt = clock.millis(),
                ),
            ),
        )
        id
    }

    suspend fun removeCycleStep(stepId: String) = db.withWriteTransaction {
        val step = programDao.getStep(stepId) ?: return@withWriteTransaction
        cycleWriter.removeSteps(step.programId, setOf(stepId))
    }

    /**
     * UNDO of [removeCycleStep]: puts the step back at [position]. [makeCurrent] is true when
     * it was the next step when it was removed, so it is next again.
     */
    suspend fun restoreCycleStep(programId: String, stepId: String, position: Int, makeCurrent: Boolean) =
        db.withWriteTransaction { cycleWriter.restoreStep(programId, stepId, position, makeCurrent) }

    /** Reorders the cycle; [orderedStepIds] holds every step id of the program in the new order. */
    suspend fun reorderCycle(programId: String, orderedStepIds: List<String>) = db.withWriteTransaction {
        cycleWriter.reorder(programId, orderedStepIds)
    }

    /**
     * Moves the program to its next cycle step (decision 29). Home's "skip" and
     * "rest day done" both call this; finishing a workout does the same inside
     * [WorkoutRepository.finishSession].
     */
    suspend fun advanceCycle(programId: String) = db.withWriteTransaction {
        val program = programDao.getById(programId) ?: return@withWriteTransaction
        val stepCount = programDao.getCycle(programId).size
        programDao.setCurrentStep(programId, CycleRules.advance(program.currentStep, stepCount), clock.millis())
    }
}
