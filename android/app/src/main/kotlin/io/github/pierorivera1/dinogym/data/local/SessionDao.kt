package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** A finished session with its routine's name and number of logged sets, for history lists. */
data class SessionListRow(
    val id: String,
    val routineId: String,
    val programId: String,
    val startedAt: Long,
    val endedAt: Long,
    val routineName: String,
    val setCount: Int,
)

/** Set 1 of an exercise in one finished session. [date] is the session's start time. */
data class SetOneRow(
    val exerciseId: String,
    val sessionId: String,
    val date: Long,
    val reps: Int,
    val weightKg: Double?,
)

/**
 * Sessions (workouts) and their logged sets. Every read skips soft-deleted rows.
 * History queries join `routine` without a `deletedAt` filter, so a session keeps
 * showing its routine's name after the routine is deleted.
 */
@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM session WHERE endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getInProgress(): SessionEntity?

    @Query("SELECT * FROM session WHERE endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("UPDATE session SET currentPosition = :position, updatedAt = :now WHERE id = :id")
    suspend fun setCurrentPosition(id: String, position: Int, now: Long)

    @Query("UPDATE session SET endedAt = :endedAt, updatedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: String, endedAt: Long)

    @Query("UPDATE session SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query(
        """
        SELECT s.id, s.routineId, s.programId, s.startedAt, s.endedAt, r.name AS routineName,
            (SELECT COUNT(*) FROM set_entry e WHERE e.sessionId = s.id AND e.deletedAt IS NULL) AS setCount
        FROM session s JOIN routine r ON r.id = s.routineId
        WHERE s.endedAt IS NOT NULL AND s.deletedAt IS NULL
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeFinished(): Flow<List<SessionListRow>>

    @Query(
        """
        SELECT s.id, s.routineId, s.programId, s.startedAt, s.endedAt, r.name AS routineName,
            (SELECT COUNT(*) FROM set_entry e WHERE e.sessionId = s.id AND e.deletedAt IS NULL) AS setCount
        FROM session s JOIN routine r ON r.id = s.routineId
        WHERE s.endedAt IS NOT NULL AND s.deletedAt IS NULL
        ORDER BY s.startedAt DESC LIMIT 1
        """,
    )
    fun observeLastFinished(): Flow<SessionListRow?>

    @Query("SELECT name FROM routine WHERE id = :routineId")
    suspend fun getRoutineName(routineId: String): String?

    // Sets

    @Insert
    suspend fun insertSet(set: SetEntryEntity)

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Query("SELECT * FROM set_entry WHERE id = :id AND deletedAt IS NULL")
    suspend fun getSet(id: String): SetEntryEntity?

    @Query("SELECT * FROM set_entry WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY exerciseId, setIndex")
    fun observeSets(sessionId: String): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM set_entry WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY exerciseId, setIndex")
    suspend fun getSets(sessionId: String): List<SetEntryEntity>

    @Query(
        """
        SELECT * FROM set_entry
        WHERE sessionId = :sessionId AND exerciseId = :exerciseId AND deletedAt IS NULL
        ORDER BY setIndex
        """,
    )
    suspend fun getSetsOfExercise(sessionId: String, exerciseId: String): List<SetEntryEntity>

    @Query(
        """
        SELECT COALESCE(MAX(setIndex), 0) FROM set_entry
        WHERE sessionId = :sessionId AND exerciseId = :exerciseId AND deletedAt IS NULL
        """,
    )
    suspend fun maxSetIndex(sessionId: String, exerciseId: String): Int

    @Query("UPDATE set_entry SET reps = :reps, weightKg = :weightKg, note = :note, updatedAt = :now WHERE id = :id")
    suspend fun updateSet(id: String, reps: Int, weightKg: Double?, note: String?, now: Long)

    /** Changes a set's numbers and keeps its note: editing a past session (Stats). */
    @Query("UPDATE set_entry SET reps = :reps, weightKg = :weightKg, updatedAt = :now WHERE id = :id")
    suspend fun updateSetValues(id: String, reps: Int, weightKg: Double?, now: Long)

    @Query("UPDATE set_entry SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun setNote(id: String, note: String?, now: Long)

    @Query("UPDATE set_entry SET setIndex = :setIndex, updatedAt = :now WHERE id = :id")
    suspend fun setSetIndex(id: String, setIndex: Int, now: Long)

    @Query("UPDATE set_entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSet(id: String, now: Long)

    @Query("UPDATE set_entry SET deletedAt = :now, updatedAt = :now WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun softDeleteSetsOfSession(sessionId: String, now: Long)

    // History queries for the training rules

    /**
     * This exercise's sets from the last finished session of [routineId] that logged it
     * and started before [before]. Feeds the pre-fill rule and the summary comparison.
     */
    @Query(
        """
        SELECT * FROM set_entry
        WHERE exerciseId = :exerciseId AND deletedAt IS NULL AND sessionId = (
            SELECT s.id FROM session s JOIN set_entry x ON x.sessionId = s.id
            WHERE s.routineId = :routineId AND s.endedAt IS NOT NULL AND s.deletedAt IS NULL
                AND s.startedAt < :before AND x.exerciseId = :exerciseId AND x.deletedAt IS NULL
            ORDER BY s.startedAt DESC LIMIT 1
        )
        ORDER BY setIndex
        """,
    )
    suspend fun lastSessionSets(routineId: String, exerciseId: String, before: Long): List<SetEntryEntity>

    /** Set 1 of [exerciseId] in every finished session that started before [before], oldest first. */
    @Query(
        """
        SELECT e.exerciseId, s.id AS sessionId, s.startedAt AS date, e.reps, e.weightKg
        FROM set_entry e JOIN session s ON s.id = e.sessionId
        WHERE e.exerciseId = :exerciseId AND e.setIndex = 1 AND e.deletedAt IS NULL
            AND s.endedAt IS NOT NULL AND s.deletedAt IS NULL AND s.startedAt < :before
        ORDER BY s.startedAt
        """,
    )
    suspend fun setOneHistory(exerciseId: String, before: Long): List<SetOneRow>

    /** Same as [setOneHistory] over all finished sessions, observed for the progress view. */
    @Query(
        """
        SELECT e.exerciseId, s.id AS sessionId, s.startedAt AS date, e.reps, e.weightKg
        FROM set_entry e JOIN session s ON s.id = e.sessionId
        WHERE e.exerciseId = :exerciseId AND e.setIndex = 1 AND e.deletedAt IS NULL
            AND s.endedAt IS NOT NULL AND s.deletedAt IS NULL
        ORDER BY s.startedAt
        """,
    )
    fun observeSetOneHistory(exerciseId: String): Flow<List<SetOneRow>>

    /** Set 1 of every exercise in every finished session of [routineId], oldest first. */
    @Query(
        """
        SELECT e.exerciseId, s.id AS sessionId, s.startedAt AS date, e.reps, e.weightKg
        FROM set_entry e JOIN session s ON s.id = e.sessionId
        WHERE s.routineId = :routineId AND e.setIndex = 1 AND e.deletedAt IS NULL
            AND s.endedAt IS NOT NULL AND s.deletedAt IS NULL
        ORDER BY s.startedAt
        """,
    )
    fun observeSetOnesOfRoutine(routineId: String): Flow<List<SetOneRow>>

    /**
     * Set 1 of every exercise in every finished session, oldest first. Feeds the `▲ n BEST`
     * tags in history, the record marks in a past session and the PROGRESS tab's bests.
     */
    @Query(
        """
        SELECT e.exerciseId, s.id AS sessionId, s.startedAt AS date, e.reps, e.weightKg
        FROM set_entry e JOIN session s ON s.id = e.sessionId
        WHERE e.setIndex = 1 AND e.deletedAt IS NULL AND s.endedAt IS NOT NULL AND s.deletedAt IS NULL
        ORDER BY s.startedAt
        """,
    )
    fun observeAllSetOnes(): Flow<List<SetOneRow>>
}
