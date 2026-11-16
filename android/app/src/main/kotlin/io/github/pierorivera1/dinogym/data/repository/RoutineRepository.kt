package io.github.pierorivera1.dinogym.data.repository

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ExerciseDao
import io.github.pierorivera1.dinogym.data.local.ProgramDao
import io.github.pierorivera1.dinogym.data.local.RoutineDao
import io.github.pierorivera1.dinogym.data.local.RoutineEntity
import io.github.pierorivera1.dinogym.data.local.RoutineExerciseEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.Routine
import io.github.pierorivera1.dinogym.domain.model.RoutineExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** One exercise of a routine with its library entry, for example "Dips, 3 × 12". */
data class RoutineItem(
    val routineExercise: RoutineExercise,
    val exercise: Exercise,
)

/** A routine with its size, for the Programs list and the program editor's steps. */
data class RoutineSummary(
    val routine: Routine,
    val exerciseCount: Int,
    /** Sets to log; show-only exercises add none. */
    val setCount: Int,
)

/** A routine with its exercises in order. */
data class RoutineDetail(
    val routine: Routine,
    val items: List<RoutineItem>,
)

/**
 * Routines and the exercises inside them, for Home (the next routine's exercises),
 * the workout (rest per exercise) and the routine editor. Writes stamp `updatedAt`;
 * deletes are soft.
 */
@Singleton
class RoutineRepository @Inject constructor(
    private val db: DinoDatabase,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val cycleWriter: CycleWriter,
    private val clock: Clock,
) {

    fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Every routine with its exercise and set counts, sorted by name. */
    fun observeRoutineSummaries(): Flow<List<RoutineSummary>> = routineDao.observeSummaries().map { rows ->
        rows.map { RoutineSummary(Routine(it.id, it.name), it.exerciseCount, it.setCount) }
    }

    /** The routine with its exercises, or null if it does not exist or was deleted. */
    fun observeRoutine(id: String): Flow<RoutineDetail?> = combine(
        routineDao.observeById(id),
        routineDao.observeItems(id),
        exerciseDao.observeAll(),
    ) { routine, items, exercises ->
        if (routine == null) return@combine null
        val byId = exercises.associateBy { it.id }
        RoutineDetail(
            routine = routine.toDomain(),
            items = items.mapNotNull { item ->
                byId[item.exerciseId]?.let { RoutineItem(item.toDomain(), it.toDomain()) }
            },
        )
    }

    suspend fun createRoutine(name: String): String {
        val id = newId()
        routineDao.insert(RoutineEntity(id = id, name = name, updatedAt = clock.millis()))
        return id
    }

    suspend fun renameRoutine(id: String, name: String) = routineDao.rename(id, name, clock.millis())

    /**
     * Soft-deletes the routine, its exercise list and every cycle step that used it
     * (each program's cycle is renumbered). Its finished sessions stay in history.
     */
    suspend fun deleteRoutine(id: String) = db.withWriteTransaction {
        val now = clock.millis()
        programDao.getStepsUsingRoutine(id)
            .groupBy { it.programId }
            .forEach { (programId, steps) -> cycleWriter.removeSteps(programId, steps.map { it.id }.toSet()) }
        routineDao.softDeleteItemsOfRoutine(id, now)
        routineDao.softDelete(id, now)
    }

    /** Adds an exercise at the end of the routine and returns the new routine-exercise id. */
    suspend fun addExercise(
        routineId: String,
        exerciseId: String,
        sets: Int,
        repMin: Int,
        repMax: Int,
        restSeconds: Int,
    ): String = db.withWriteTransaction {
        val id = newId()
        val position = routineDao.getItems(routineId).size
        routineDao.insertItems(
            listOf(
                RoutineExerciseEntity(
                    id = id,
                    routineId = routineId,
                    exerciseId = exerciseId,
                    position = position,
                    sets = sets,
                    repMin = repMin,
                    repMax = repMax,
                    restSeconds = restSeconds,
                    updatedAt = clock.millis(),
                ),
            ),
        )
        id
    }

    /** Edits the target of one routine exercise. A fixed target has [repMin] == [repMax]. */
    suspend fun updateTarget(routineExerciseId: String, sets: Int, repMin: Int, repMax: Int, restSeconds: Int) =
        routineDao.updateTarget(routineExerciseId, sets, repMin, repMax, restSeconds, clock.millis())

    /** Changes the rest after this exercise; the rest picker in the workout calls this. */
    suspend fun setRestSeconds(routineExerciseId: String, restSeconds: Int) =
        routineDao.setRestSeconds(routineExerciseId, restSeconds, clock.millis())

    /** Removes an exercise from the routine and renumbers the rest. */
    suspend fun removeExercise(routineExerciseId: String) = db.withWriteTransaction {
        val item = routineDao.getItem(routineExerciseId) ?: return@withWriteTransaction
        val now = clock.millis()
        routineDao.softDeleteItem(routineExerciseId, now)
        routineDao.getItems(item.routineId).forEachIndexed { index, remaining ->
            if (remaining.position != index) routineDao.setItemPosition(remaining.id, index, now)
        }
    }

    /** UNDO of [removeExercise]: puts the exercise back at [position] in [routineId] and renumbers. */
    suspend fun restoreExercise(routineId: String, routineExerciseId: String, position: Int) = db.withWriteTransaction {
        val items = routineDao.getItems(routineId).filterNot { it.id == routineExerciseId }
        val index = position.coerceIn(0, items.size)
        val now = clock.millis()
        routineDao.restoreItem(routineExerciseId, index, now)
        items.forEachIndexed { i, item ->
            val target = if (i < index) i else i + 1
            if (item.position != target) routineDao.setItemPosition(item.id, target, now)
        }
    }

    /** Puts the routine's exercises in the order of [orderedIds] (every routine-exercise id of it). */
    suspend fun reorderExercises(routineId: String, orderedIds: List<String>) = db.withWriteTransaction {
        val items = routineDao.getItems(routineId)
        require(orderedIds.toSet() == items.map { it.id }.toSet()) {
            "orderedIds must contain exactly the routine's exercises"
        }
        val now = clock.millis()
        val positions = items.associate { it.id to it.position }
        orderedIds.forEachIndexed { index, id ->
            if (positions[id] != index) routineDao.setItemPosition(id, index, now)
        }
    }
}
