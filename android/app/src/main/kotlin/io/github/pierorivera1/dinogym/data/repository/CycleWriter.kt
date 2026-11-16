package io.github.pierorivera1.dinogym.data.repository

import io.github.pierorivera1.dinogym.data.local.ProgramDao
import io.github.pierorivera1.dinogym.domain.rules.CycleRules
import java.time.Clock
import javax.inject.Inject

/**
 * Changes to a program's cycle that keep its invariants: step positions stay
 * 0, 1, 2… and `currentStep` keeps pointing at the same step when others move.
 *
 * Used by [ProgramRepository] and by [RoutineRepository] (deleting a routine removes
 * its steps). Every function must be called inside a write transaction.
 */
class CycleWriter @Inject constructor(
    private val programDao: ProgramDao,
    private val clock: Clock,
) {

    /**
     * Soft-deletes [stepIds] from [programId]'s cycle and renumbers the steps that remain.
     * `currentStep` follows [CycleRules.afterRemoval]: the same step stays next, or the one
     * after it when the next step itself is removed.
     */
    suspend fun removeSteps(programId: String, stepIds: Set<String>) {
        val now = clock.millis()
        val program = programDao.getById(programId) ?: return
        val steps = programDao.getCycle(programId)
        val removed = steps.indices.filter { steps[it].id in stepIds }.toSet()
        if (removed.isEmpty()) return

        removed.forEach { programDao.softDeleteStep(steps[it].id, now) }
        steps.filterNot { it.id in stepIds }.forEachIndexed { index, step ->
            if (step.position != index) programDao.setStepPosition(step.id, index, now)
        }
        val current = CycleRules.afterRemoval(program.currentStep, steps.size, removed)
        if (current != program.currentStep) programDao.setCurrentStep(programId, current, now)
    }

    /**
     * Puts a removed step back at [position] (UNDO): clears its `deletedAt` and moves the
     * steps from [position] on one place down. With [makeCurrent] it becomes the next step
     * again, as it was when it was removed.
     */
    suspend fun restoreStep(programId: String, stepId: String, position: Int, makeCurrent: Boolean) {
        val now = clock.millis()
        val program = programDao.getById(programId) ?: return
        val steps = programDao.getCycle(programId).filterNot { it.id == stepId }
        val index = position.coerceIn(0, steps.size)
        programDao.restoreStep(stepId, index, now)
        steps.forEachIndexed { i, step ->
            val target = if (i < index) i else i + 1
            if (step.position != target) programDao.setStepPosition(step.id, target, now)
        }
        val current = CycleRules.afterInsert(program.currentStep, steps.size, index, makeCurrent)
        if (current != program.currentStep) programDao.setCurrentStep(programId, current, now)
    }

    /**
     * Puts the steps of [programId] in the order of [orderedStepIds] (which must hold
     * every step id of the program). The current step follows its step to its new place.
     */
    suspend fun reorder(programId: String, orderedStepIds: List<String>) {
        val now = clock.millis()
        val program = programDao.getById(programId) ?: return
        val steps = programDao.getCycle(programId)
        require(orderedStepIds.toSet() == steps.map { it.id }.toSet()) {
            "orderedStepIds must contain exactly the program's steps"
        }
        val currentId = steps.getOrNull(CycleRules.currentIndex(program.currentStep, steps.size))?.id

        val positions = steps.associate { it.id to it.position }
        orderedStepIds.forEachIndexed { index, id ->
            if (positions[id] != index) programDao.setStepPosition(id, index, now)
        }
        val newCurrent = orderedStepIds.indexOf(currentId)
        if (currentId != null && newCurrent != program.currentStep) {
            programDao.setCurrentStep(programId, newCurrent, now)
        }
    }
}
