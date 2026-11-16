package io.github.pierorivera1.dinogym.data.repository

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ExerciseDao
import io.github.pierorivera1.dinogym.data.local.ProgramDao
import io.github.pierorivera1.dinogym.data.local.RoutineDao
import io.github.pierorivera1.dinogym.data.local.SessionDao
import io.github.pierorivera1.dinogym.data.local.SessionEntity
import io.github.pierorivera1.dinogym.data.local.SetEntryEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.Routine
import io.github.pierorivera1.dinogym.domain.model.RoutineExercise
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.domain.model.SetEntry
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import io.github.pierorivera1.dinogym.domain.model.SetValues
import io.github.pierorivera1.dinogym.domain.rules.CycleRules
import io.github.pierorivera1.dinogym.domain.rules.PreFill
import io.github.pierorivera1.dinogym.domain.rules.SetOneBests
import io.github.pierorivera1.dinogym.domain.rules.WeightField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** One exercise of the workout in progress, with everything the active-exercise screen shows. */
data class WorkoutExercise(
    val routineExercise: RoutineExercise,
    val exercise: Exercise,
    /** Sets logged in this session, by set index. */
    val loggedSets: List<SetEntry>,
    /** This exercise's sets in the last finished session of the same routine. */
    val lastSessionSets: List<SetEntry>,
    /** Set 1 of this exercise in every earlier finished session, oldest first. */
    val setOneHistory: List<SetOneResult>,
) {
    /** The values to pre-fill for set [setIndex] (1-based), per the pre-fill rule. */
    fun preFill(setIndex: Int): SetValues =
        PreFill.valuesFor(setIndex, lastSessionSets, routineExercise.repMin)

    /** The best set 1 before this session, or null with no history. */
    val previousBest: SetOneResult? get() = SetOneBests.bests(setOneHistory).lastOrNull()

    /** Whether a set 1 of [reps] at [weightKg] earns the "NEW BEST" tag. */
    fun isNewBest(reps: Int, weightKg: Double?): Boolean =
        SetOneBests.isNewBest(SetOneResult(sessionId = "", date = 0, reps = reps, weightKg = weightKg), setOneHistory)
}

/** The workout in progress. [Session.currentPosition] indexes [exercises]. */
data class ActiveWorkout(
    val session: Session,
    val routine: Routine,
    val programType: ProgramType,
    val exercises: List<WorkoutExercise>,
) {
    /** The exercise the workout is on, or null once it moved past the last one. */
    val currentExercise: WorkoutExercise? get() = exercises.getOrNull(session.currentPosition)

    fun showsWeightField(exercise: WorkoutExercise): Boolean =
        WeightField.isShown(programType, exercise.exercise.weightEnabled)
}

/**
 * The workout in progress: start, log sets as they happen, move between exercises,
 * finish or discard. Every set is written immediately, so a workout survives the
 * app being killed. At most one session is in progress at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WorkoutRepository @Inject constructor(
    private val db: DinoDatabase,
    private val sessionDao: SessionDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val clock: Clock,
) {

    /** The workout in progress, or null when there is none. */
    fun observeActiveWorkout(): Flow<ActiveWorkout?> =
        sessionDao.observeInProgress().flatMapLatest { session ->
            if (session == null) flowOf(null) else observeWorkout(session)
        }

    private fun observeWorkout(session: SessionEntity): Flow<ActiveWorkout?> = combine(
        routineDao.observeById(session.routineId),
        routineDao.observeItems(session.routineId),
        exerciseDao.observeAll(),
        sessionDao.observeSets(session.id),
        programDao.observeById(session.programId),
    ) { routine, items, exercises, sets, program ->
        // The routine may have been deleted mid-workout; history keeps its name.
        val routineName = routine?.name ?: sessionDao.getRoutineName(session.routineId) ?: ""
        val exerciseById = exercises.associateBy { it.id }
        val setsByExercise = sets.map { it.toDomain() }.groupBy { it.exerciseId }
        ActiveWorkout(
            session = session.toDomain(),
            routine = Routine(session.routineId, routineName),
            programType = program?.toDomain()?.type ?: ProgramType.CALISTHENICS,
            exercises = items.mapNotNull { item ->
                val exercise = exerciseById[item.exerciseId] ?: return@mapNotNull null
                WorkoutExercise(
                    routineExercise = item.toDomain(),
                    exercise = exercise.toDomain(),
                    loggedSets = setsByExercise[item.exerciseId].orEmpty().sortedBy { it.setIndex },
                    lastSessionSets = sessionDao
                        .lastSessionSets(session.routineId, item.exerciseId, before = session.startedAt)
                        .map { it.toDomain() },
                    setOneHistory = sessionDao
                        .setOneHistory(item.exerciseId, before = session.startedAt)
                        .map { it.toDomain() },
                )
            },
        )
    }

    /**
     * Starts the next routine of the active program and returns the session id.
     * Returns null when no program is active or the next step is a rest day or the
     * cycle is empty. If a workout is already in progress, returns that one instead.
     */
    suspend fun startNextWorkout(): String? = db.withWriteTransaction {
        sessionDao.getInProgress()?.let { return@withWriteTransaction it.id }
        val program = programDao.getActive() ?: return@withWriteTransaction null
        val cycle = programDao.getCycle(program.id)
        val step = cycle.getOrNull(CycleRules.currentIndex(program.currentStep, cycle.size))
        val routineId = step?.routineId ?: return@withWriteTransaction null
        insertSession(program.id, routineId)
    }

    /**
     * Starts [routineId] as part of [programId] and returns the session id. If a
     * workout is already in progress, returns that one instead of starting a second.
     */
    suspend fun startSession(programId: String, routineId: String): String = db.withWriteTransaction {
        sessionDao.getInProgress()?.id ?: insertSession(programId, routineId)
    }

    private suspend fun insertSession(programId: String, routineId: String): String {
        val now = clock.millis()
        val id = newId()
        sessionDao.insert(
            SessionEntity(
                id = id,
                routineId = routineId,
                programId = programId,
                startedAt = now,
                endedAt = null,
                currentPosition = 0,
                updatedAt = now,
            ),
        )
        return id
    }

    /** Logs the next set of [exerciseId] in [sessionId] and returns the set id. */
    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        reps: Int,
        weightKg: Double?,
        note: String? = null,
    ): String = db.withWriteTransaction {
        val id = newId()
        sessionDao.insertSet(
            SetEntryEntity(
                id = id,
                sessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = sessionDao.maxSetIndex(sessionId, exerciseId) + 1,
                reps = reps,
                weightKg = weightKg,
                note = note,
                updatedAt = clock.millis(),
            ),
        )
        id
    }

    /** Edits a logged set, in the workout or in a past session (Stats). */
    suspend fun updateSet(setId: String, reps: Int, weightKg: Double?, note: String?) =
        sessionDao.updateSet(setId, reps, weightKg, note, clock.millis())

    suspend fun setNote(setId: String, note: String?) = sessionDao.setNote(setId, note, clock.millis())

    /**
     * Deletes a logged set and renumbers that exercise's remaining sets 1, 2, 3…,
     * so set 1 always exists while the exercise has any set.
     */
    suspend fun deleteSet(setId: String) = db.withWriteTransaction {
        val set = sessionDao.getSet(setId) ?: return@withWriteTransaction
        val now = clock.millis()
        sessionDao.softDeleteSet(setId, now)
        sessionDao.getSetsOfExercise(set.sessionId, set.exerciseId).forEachIndexed { index, remaining ->
            if (remaining.setIndex != index + 1) sessionDao.setSetIndex(remaining.id, index + 1, now)
        }
    }

    /** Moves the workout to the exercise at [position] (skip, next, or going back). */
    suspend fun setCurrentPosition(sessionId: String, position: Int) =
        sessionDao.setCurrentPosition(sessionId, position.coerceAtLeast(0), clock.millis())

    /**
     * Ends the workout and moves the program's cycle to the next step, in one
     * transaction: either both happen or neither does. Finishing twice does nothing.
     */
    suspend fun finishSession(sessionId: String) = db.withWriteTransaction {
        val session = sessionDao.getById(sessionId) ?: return@withWriteTransaction
        if (session.endedAt != null) return@withWriteTransaction
        val now = clock.millis()
        sessionDao.finish(sessionId, now)
        val program = programDao.getById(session.programId) ?: return@withWriteTransaction
        val stepCount = programDao.getCycle(program.id).size
        programDao.setCurrentStep(program.id, CycleRules.advance(program.currentStep, stepCount), now)
    }

    /** Throws the workout away: soft-deletes the session and its sets. The cycle does not move. */
    suspend fun discardSession(sessionId: String) = db.withWriteTransaction {
        val now = clock.millis()
        sessionDao.softDeleteSetsOfSession(sessionId, now)
        sessionDao.softDelete(sessionId, now)
    }
}
