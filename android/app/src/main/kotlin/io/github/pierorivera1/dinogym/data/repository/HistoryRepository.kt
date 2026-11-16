package io.github.pierorivera1.dinogym.data.repository

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ExerciseDao
import io.github.pierorivera1.dinogym.data.local.ProgramDao
import io.github.pierorivera1.dinogym.data.local.RoutineDao
import io.github.pierorivera1.dinogym.data.local.SessionDao
import io.github.pierorivera1.dinogym.data.local.SessionListRow
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.domain.model.SetEntry
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import io.github.pierorivera1.dinogym.domain.rules.CycleRules
import io.github.pierorivera1.dinogym.domain.rules.ProgressRules
import io.github.pierorivera1.dinogym.domain.rules.SetOneBests
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** A finished session in a history list. */
data class SessionListItem(
    val session: Session,
    val routineName: String,
    val setCount: Int,
)

/** One exercise's sets inside a session. */
data class ExerciseSets(
    val exercise: Exercise,
    val sets: List<SetEntry>,
)

/** A session with its sets grouped by exercise, in routine order. */
data class SessionDetail(
    val session: Session,
    val routineName: String,
    val exercises: List<ExerciseSets>,
)

/** Set 1 of one exercise in a session against the previous session of the same routine. */
data class SetOneComparison(
    val exercise: Exercise,
    /** Null if set 1 was not logged in this session. */
    val setOne: SetEntry?,
    /** Null if the previous session of this routine did not log the exercise. */
    val previousSetOne: SetEntry?,
    val isNewBest: Boolean,
)

/** What the summary screen shows after a workout. */
data class SessionSummary(
    val session: Session,
    val routineName: String,
    val durationMillis: Long,
    val setCount: Int,
    val exercises: List<SetOneComparison>,
    val newBestCount: Int,
    /** The program's next step after this workout, or null if the program or its cycle is gone. */
    val nextStep: CycleStepInfo?,
)

/**
 * Finished sessions: history by date, one session's sets, the post-workout summary,
 * progress records (set-1 bests) and Home's "last time" numbers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HistoryRepository @Inject constructor(
    private val db: DinoDatabase,
    private val sessionDao: SessionDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val clock: Clock,
) {

    /** Finished sessions, newest first. */
    fun observeFinishedSessions(): Flow<List<SessionListItem>> =
        sessionDao.observeFinished().map { rows -> rows.map { it.toListItem() } }

    /** The most recent finished session, for Home. */
    fun observeLastFinishedSession(): Flow<SessionListItem?> =
        sessionDao.observeLastFinished().map { it?.toListItem() }

    private fun SessionListRow.toListItem() = SessionListItem(toDomain(), routineName, setCount)

    /** One session with its sets grouped by exercise (in the routine's order), or null if deleted. */
    fun observeSessionDetail(sessionId: String): Flow<SessionDetail?> =
        sessionDao.observeById(sessionId).flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    sessionDao.observeSets(sessionId),
                    routineDao.observeItems(session.routineId),
                    exerciseDao.observeAll(),
                ) { sets, items, exercises ->
                    val order = items.associate { it.exerciseId to it.position }
                    val byId = exercises.associateBy { it.id }
                    SessionDetail(
                        session = session.toDomain(),
                        routineName = sessionDao.getRoutineName(session.routineId).orEmpty(),
                        exercises = sets.map { it.toDomain() }
                            .groupBy { it.exerciseId }
                            .entries
                            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
                            .mapNotNull { (exerciseId, exerciseSets) ->
                                byId[exerciseId]?.let { ExerciseSets(it.toDomain(), exerciseSets.sortedBy { s -> s.setIndex }) }
                            },
                    )
                }
            }
        }

    /** Deletes a finished session and its sets (soft). The program cycle does not move back. */
    suspend fun deleteSession(sessionId: String) = db.withWriteTransaction {
        val now = clock.millis()
        sessionDao.softDeleteSetsOfSession(sessionId, now)
        sessionDao.softDelete(sessionId, now)
    }

    /**
     * Changes a logged set of a past session; the note stays. Progress is a query over
     * set 1 (decision 28), so a set-1 change recomputes it with nothing else to update.
     */
    suspend fun updateSetValues(setId: String, reps: Int, weightKg: Double?) =
        sessionDao.updateSetValues(setId, reps, weightKg, clock.millis())

    /** Set 1 of every finished session, grouped by exercise id, each list oldest first. */
    private fun observeSetOnesByExercise(): Flow<Map<String, List<SetOneResult>>> =
        sessionDao.observeAllSetOnes().map { rows ->
            rows.groupBy({ it.exerciseId }, { it.toDomain() })
        }

    /** Session id → the exercises whose set 1 set a new best in it (history tags, past session marks). */
    fun observeNewBestsBySession(): Flow<Map<String, Set<String>>> =
        observeSetOnesByExercise().map(ProgressRules::newBestsBySession)

    /** Exercise id → its current best set 1, for the PROGRESS tab. Exercises never logged are missing. */
    fun observeBestSetOnes(): Flow<Map<String, SetOneResult>> =
        observeSetOnesByExercise().map { byExercise ->
            byExercise.mapNotNull { (id, history) -> SetOneBests.bests(history).lastOrNull()?.let { id to it } }.toMap()
        }

    /** The progress records of an exercise: the days set 1 beat the previous best, oldest first. */
    fun observeSetOneBests(exerciseId: String): Flow<List<SetOneResult>> =
        sessionDao.observeSetOneHistory(exerciseId).map { rows -> SetOneBests.bests(rows.map { it.toDomain() }) }

    /** Every set 1 of an exercise, oldest first, for the progress chart. */
    fun observeSetOneHistory(exerciseId: String): Flow<List<SetOneResult>> =
        sessionDao.observeSetOneHistory(exerciseId).map { rows -> rows.map { it.toDomain() } }

    /**
     * For Home: per exercise id, set 1 of the last finished session of [routineId]
     * that logged it.
     */
    fun observeLastSetOnes(routineId: String): Flow<Map<String, SetOneResult>> =
        sessionDao.observeSetOnesOfRoutine(routineId).map { rows ->
            // Rows are oldest first, so associate keeps the latest per exercise.
            rows.associate { it.exerciseId to it.toDomain() }
        }

    /** The summary of a finished session, or null if it does not exist. */
    suspend fun getSummary(sessionId: String): SessionSummary? {
        val session = sessionDao.getById(sessionId) ?: return null
        val sets = sessionDao.getSets(sessionId).map { it.toDomain() }
        val order = routineDao.getItems(session.routineId).associate { it.exerciseId to it.position }
        val setsByExercise = sets.groupBy { it.exerciseId }
        val exercises = exerciseDao.getByIds(setsByExercise.keys.toList()).associateBy { it.id }

        val comparisons = setsByExercise.keys
            .sortedBy { order[it] ?: Int.MAX_VALUE }
            .mapNotNull { exerciseId ->
                val exercise = exercises[exerciseId] ?: return@mapNotNull null
                val setOne = setsByExercise[exerciseId]?.firstOrNull { it.setIndex == 1 }
                val previousSetOne = sessionDao
                    .lastSessionSets(session.routineId, exerciseId, before = session.startedAt)
                    .firstOrNull()?.toDomain()
                val history = sessionDao.setOneHistory(exerciseId, before = session.startedAt).map { it.toDomain() }
                val isNewBest = setOne != null && SetOneBests.isNewBest(
                    SetOneResult(session.id, session.startedAt, setOne.reps, setOne.weightKg),
                    history,
                )
                SetOneComparison(exercise.toDomain(), setOne, previousSetOne, isNewBest)
            }

        val program = programDao.getById(session.programId)
        val nextStep = program?.let { p ->
            val cycle = programDao.getCycle(p.id)
            cycle.getOrNull(CycleRules.currentIndex(p.currentStep, cycle.size))?.let { step ->
                CycleStepInfo(step.toDomain(), step.routineId?.let { routineDao.getById(it)?.name })
            }
        }

        return SessionSummary(
            session = session.toDomain(),
            routineName = sessionDao.getRoutineName(session.routineId).orEmpty(),
            durationMillis = (session.endedAt ?: clock.millis()) - session.startedAt,
            setCount = sets.size,
            exercises = comparisons,
            newBestCount = comparisons.count { it.isNewBest },
            nextStep = nextStep,
        )
    }
}
