package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** A row's `updatedAt` and `deletedAt`: all the merge rules need to know about it. */
data class RowVersion(val updatedAt: Long, val deletedAt: Long?)

/**
 * Everything sync reads and writes in Room (task 21). Queries are written per table, so Room
 * checks each one at build time. Repositories never use this DAO; the triggers in
 * [SyncTracking] fill `sync_pending` for them.
 */
@Dao
interface SyncDao {

    // Pending changes (decision 88)

    @Query("SELECT * FROM sync_pending")
    suspend fun pendingEntries(): List<SyncPendingEntity>

    @Query("SELECT COUNT(*) FROM sync_pending")
    fun observePendingCount(): Flow<Int>

    /** The newest change's `seq`, or null when nothing is waiting. Moves on every synced write. */
    @Query("SELECT MAX(seq) FROM sync_pending")
    fun observeLatestPending(): Flow<Long?>

    @Query("SELECT MAX(seq) FROM sync_pending")
    suspend fun latestPending(): Long?

    /** Clears an entry the server took, unless the row changed again after it was read ([seq]). */
    @Query("DELETE FROM sync_pending WHERE tableName = :table AND rowId = :id AND seq <= :seq")
    suspend fun clearPushed(table: String, id: String, seq: Long)

    /** Drops an entry outright: a pulled row replaced the local one, or the row is gone. */
    @Query("DELETE FROM sync_pending WHERE tableName = :table AND rowId = :id")
    suspend fun forgetPending(table: String, id: String)

    @Query("SELECT * FROM program WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'program')")
    suspend fun pendingPrograms(): List<ProgramEntity>

    @Query("SELECT * FROM cycle_step WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'cycle_step')")
    suspend fun pendingCycleSteps(): List<CycleStepEntity>

    @Query("SELECT * FROM routine WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'routine')")
    suspend fun pendingRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM exercise WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'exercise')")
    suspend fun pendingExercises(): List<ExerciseEntity>

    @Query(
        "SELECT * FROM routine_exercise WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'routine_exercise')",
    )
    suspend fun pendingRoutineExercises(): List<RoutineExerciseEntity>

    @Query("SELECT * FROM session WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'session')")
    suspend fun pendingSessions(): List<SessionEntity>

    @Query("SELECT * FROM set_entry WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'set_entry')")
    suspend fun pendingSetEntries(): List<SetEntryEntity>

    @Query("SELECT * FROM weigh_in WHERE id IN (SELECT rowId FROM sync_pending WHERE tableName = 'weigh_in')")
    suspend fun pendingWeighIns(): List<WeighInEntity>

    @Query("SELECT * FROM preference WHERE `key` IN (SELECT rowId FROM sync_pending WHERE tableName = 'preference')")
    suspend fun pendingPreferences(): List<PreferenceEntity>

    // Marking the whole history for upload (a new account on this phone, decision 90)

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'program', id FROM program")
    suspend fun markAllPrograms()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'cycle_step', id FROM cycle_step")
    suspend fun markAllCycleSteps()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'routine', id FROM routine")
    suspend fun markAllRoutines()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'exercise', id FROM exercise")
    suspend fun markAllExercises()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'routine_exercise', id FROM routine_exercise")
    suspend fun markAllRoutineExercises()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'session', id FROM session")
    suspend fun markAllSessions()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'set_entry', id FROM set_entry")
    suspend fun markAllSetEntries()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'weigh_in', id FROM weigh_in")
    suspend fun markAllWeighIns()

    @Query("INSERT OR REPLACE INTO sync_pending (tableName, rowId) SELECT 'preference', `key` FROM preference")
    suspend fun markAllPreferences()

    // Versions, for the merge rules (decision 51)

    @Query("SELECT updatedAt, deletedAt FROM program WHERE id = :id")
    suspend fun programVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM cycle_step WHERE id = :id")
    suspend fun cycleStepVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM routine WHERE id = :id")
    suspend fun routineVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM exercise WHERE id = :id")
    suspend fun exerciseVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM routine_exercise WHERE id = :id")
    suspend fun routineExerciseVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM session WHERE id = :id")
    suspend fun sessionVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM set_entry WHERE id = :id")
    suspend fun setEntryVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM weigh_in WHERE id = :id")
    suspend fun weighInVersion(id: String): RowVersion?

    @Query("SELECT updatedAt, deletedAt FROM preference WHERE `key` = :key")
    suspend fun preferenceVersion(key: String): RowVersion?

    // Writing pulled rows. Upsert updates in place, so a parent with children is never deleted.

    @Upsert
    suspend fun upsertProgram(row: ProgramEntity)

    @Upsert
    suspend fun upsertCycleStep(row: CycleStepEntity)

    @Upsert
    suspend fun upsertRoutine(row: RoutineEntity)

    @Upsert
    suspend fun upsertExercise(row: ExerciseEntity)

    @Upsert
    suspend fun upsertRoutineExercise(row: RoutineExerciseEntity)

    @Upsert
    suspend fun upsertSession(row: SessionEntity)

    @Upsert
    suspend fun upsertSetEntry(row: SetEntryEntity)

    @Upsert
    suspend fun upsertWeighIn(row: WeighInEntity)

    @Upsert
    suspend fun upsertPreference(row: PreferenceEntity)

    // Rows waiting for their parent (decision 89)

    @Query("SELECT * FROM sync_inbox")
    suspend fun inbox(): List<SyncInboxEntity>

    @Upsert
    suspend fun putInbox(row: SyncInboxEntity)

    @Query("DELETE FROM sync_inbox WHERE tableName = :table AND rowId = :id")
    suspend fun removeInbox(table: String, id: String)

    @Query("DELETE FROM sync_inbox")
    suspend fun clearInbox()
}
