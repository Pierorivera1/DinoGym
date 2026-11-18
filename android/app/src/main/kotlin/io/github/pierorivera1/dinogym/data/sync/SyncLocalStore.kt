package io.github.pierorivera1.dinogym.data.sync

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.models.ChangeSet
import io.github.pierorivera1.dinogym.api.models.SyncTable
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.LocalFlagDao
import io.github.pierorivera1.dinogym.data.local.LocalFlagEntity
import io.github.pierorivera1.dinogym.data.local.RowVersion
import io.github.pierorivera1.dinogym.data.local.SyncDao
import io.github.pierorivera1.dinogym.data.local.SyncInboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** A row waiting to be pushed, with the `seq` of the change that was read (decision 88). */
data class PendingRow(val row: SyncRow, val seq: Long)

/** What the phone knows about its own sync, for the ACCOUNT card. */
data class LocalSyncState(
    val pending: Int = 0,
    val lastSyncedAt: Long? = null,
    /** The last sync ended in an error (not just offline). Cleared by the next success. */
    val failed: Boolean = false,
)

/**
 * Sync's view of the local database. An interface so the engine is tested on the JVM with an
 * in-memory fake; [RoomSyncLocalStore] is the real one (its SQL is checked in androidTest).
 */
interface SyncLocalStore {
    /** Every pending row, parents first. A snapshot: rows changed later are not in it. */
    suspend fun pendingRows(): List<PendingRow>

    /** The newest pending change's `seq`, 0 when nothing waits. */
    suspend fun latestPending(): Long

    /** Moves on every synced write; null when nothing waits. */
    fun observeLatestPending(): Flow<Long?>

    /** The server took these rows (`accepted` or `stale`): clear them unless changed since. */
    suspend fun clearPushed(rows: List<PendingRow>)

    suspend fun cursor(): Long

    /** Applies a pulled page and stores [cursor], in one transaction. */
    suspend fun applyPage(rows: List<SyncRow>, cursor: Long): PageApplier.Counts

    /**
     * Called when [userId] signs in. When this phone last synced with another account, or
     * never, every row is marked pending (the whole history is uploaded), the cursor goes back
     * to 0 and the sync status is cleared; returns true. The same account again: nothing
     * changes, so pending changes from before a sign-out still go up; returns false.
     */
    suspend fun startAccount(userId: String): Boolean

    suspend fun recordSuccess(at: Long)

    suspend fun recordFailure(code: String)

    val state: Flow<LocalSyncState>
}

@Singleton
class RoomSyncLocalStore @Inject constructor(
    private val db: DinoDatabase,
) : SyncLocalStore {

    private val dao: SyncDao = db.syncDao()
    private val flags: LocalFlagDao = db.localFlagDao()

    override suspend fun pendingRows(): List<PendingRow> = db.withReadTransaction {
        val seqs = dao.pendingEntries().associate { (it.tableName to it.rowId) to it.seq }
        val rows: List<SyncRow> = buildList {
            dao.pendingPrograms().forEach { add(ProgramRow(it)) }
            dao.pendingRoutines().forEach { add(RoutineRow(it)) }
            dao.pendingExercises().forEach { add(ExerciseRow(it)) }
            dao.pendingCycleSteps().forEach { add(CycleStepRow(it)) }
            dao.pendingRoutineExercises().forEach { add(RoutineExerciseRow(it)) }
            dao.pendingSessions().forEach { add(SessionRow(it)) }
            dao.pendingSetEntries().forEach { add(SetEntryRow(it)) }
            dao.pendingWeighIns().forEach { add(WeighInRow(it)) }
            dao.pendingPreferences().forEach { add(PreferenceRow(it)) }
        }
        rows.mapNotNull { row -> seqs[row.ref.table.value to row.ref.id]?.let { PendingRow(row, it) } }
            .sortedWith(compareBy({ TABLE_ORDER.indexOf(it.row.ref.table) }, { it.seq }))
    }

    override suspend fun latestPending(): Long = dao.latestPending() ?: 0

    override fun observeLatestPending(): Flow<Long?> = dao.observeLatestPending()

    override suspend fun clearPushed(rows: List<PendingRow>) {
        db.withWriteTransaction {
            rows.forEach { dao.clearPushed(it.row.ref.table.value, it.row.ref.id, it.seq) }
        }
    }

    override suspend fun cursor(): Long = flags.get(KEY_CURSOR)?.toLongOrNull() ?: 0

    override suspend fun applyPage(rows: List<SyncRow>, cursor: Long): PageApplier.Counts =
        db.withWriteTransaction {
            val counts = PageApplier.apply(rows, RoomApplyTarget())
            flags.put(LocalFlagEntity(KEY_CURSOR, cursor.toString()))
            counts
        }

    override suspend fun startAccount(userId: String): Boolean = db.withWriteTransaction {
        if (flags.get(KEY_USER) == userId) return@withWriteTransaction false
        dao.markAllPrograms()
        dao.markAllRoutines()
        dao.markAllExercises()
        dao.markAllCycleSteps()
        dao.markAllRoutineExercises()
        dao.markAllSessions()
        dao.markAllSetEntries()
        dao.markAllWeighIns()
        dao.markAllPreferences()
        dao.clearInbox()
        flags.put(LocalFlagEntity(KEY_CURSOR, "0"))
        flags.delete(KEY_LAST_SUCCESS)
        flags.delete(KEY_LAST_ERROR)
        flags.put(LocalFlagEntity(KEY_USER, userId))
        true
    }

    override suspend fun recordSuccess(at: Long) {
        db.withWriteTransaction {
            flags.put(LocalFlagEntity(KEY_LAST_SUCCESS, at.toString()))
            flags.delete(KEY_LAST_ERROR)
        }
    }

    override suspend fun recordFailure(code: String) {
        flags.put(LocalFlagEntity(KEY_LAST_ERROR, code))
    }

    override val state: Flow<LocalSyncState> = combine(
        dao.observePendingCount(),
        flags.observe(KEY_LAST_SUCCESS),
        flags.observe(KEY_LAST_ERROR),
    ) { pending, last, error -> LocalSyncState(pending, last?.toLongOrNull(), error != null) }

    /** The database side of [PageApplier]; only used inside [applyPage]'s transaction. */
    private inner class RoomApplyTarget : ApplyTarget {
        override suspend fun version(ref: RowRef): RowVersion? = when (ref.table) {
            SyncTable.PROGRAM -> dao.programVersion(ref.id)
            SyncTable.CYCLE_STEP -> dao.cycleStepVersion(ref.id)
            SyncTable.ROUTINE -> dao.routineVersion(ref.id)
            SyncTable.EXERCISE -> dao.exerciseVersion(ref.id)
            SyncTable.ROUTINE_EXERCISE -> dao.routineExerciseVersion(ref.id)
            SyncTable.SESSION -> dao.sessionVersion(ref.id)
            SyncTable.SET_ENTRY -> dao.setEntryVersion(ref.id)
            SyncTable.WEIGH_IN -> dao.weighInVersion(ref.id)
            SyncTable.PREFERENCE -> dao.preferenceVersion(ref.id)
        }

        override suspend fun write(row: SyncRow) {
            when (row) {
                is ProgramRow -> dao.upsertProgram(row.entity)
                is CycleStepRow -> dao.upsertCycleStep(row.entity)
                is RoutineRow -> dao.upsertRoutine(row.entity)
                is ExerciseRow -> dao.upsertExercise(row.entity)
                is RoutineExerciseRow -> dao.upsertRoutineExercise(row.entity)
                is SessionRow -> dao.upsertSession(row.entity)
                is SetEntryRow -> dao.upsertSetEntry(row.entity)
                is WeighInRow -> dao.upsertWeighIn(row.entity)
                is PreferenceRow -> dao.upsertPreference(row.entity)
            }
            // The trigger just marked the row; it came from the server, so nothing to push.
            dao.forgetPending(row.ref.table.value, row.ref.id)
        }

        override suspend fun inbox(): List<SyncRow> =
            dao.inbox().flatMap { DinoGymApi.JSON.decodeFromString<ChangeSet>(it.json).toRows() }

        override suspend fun putInbox(row: SyncRow) {
            dao.putInbox(
                SyncInboxEntity(row.ref.table.value, row.ref.id, DinoGymApi.JSON.encodeToString(listOf(row).toChangeSet())),
            )
        }

        override suspend fun removeInbox(ref: RowRef) {
            dao.removeInbox(ref.table.value, ref.id)
        }
    }

    private companion object {
        const val KEY_USER = "sync_user_id"
        const val KEY_CURSOR = "sync_cursor"
        const val KEY_LAST_SUCCESS = "sync_last_success_at"
        const val KEY_LAST_ERROR = "sync_last_error"
    }
}
