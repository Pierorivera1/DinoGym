package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.data.local.ExerciseEntity
import io.github.pierorivera1.dinogym.data.local.ProgramEntity
import io.github.pierorivera1.dinogym.data.local.RoutineEntity
import io.github.pierorivera1.dinogym.data.local.RowVersion
import io.github.pierorivera1.dinogym.data.local.SessionEntity
import io.github.pierorivera1.dinogym.data.local.SetEntryEntity
import io.github.pierorivera1.dinogym.data.local.WeighInEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [SyncLocalStore] in memory, with the same rules as the Room one: [write] plays the part of
 * the triggers (every local write marks the row with a new seq) and [applyPage] runs the real
 * [PageApplier]. The Room SQL itself is checked in androidTest.
 */
class FakeSyncLocalStore : SyncLocalStore {
    val rows = LinkedHashMap<RowRef, SyncRow>()
    val pending = LinkedHashMap<RowRef, Long>()
    val inbox = LinkedHashMap<RowRef, SyncRow>()
    var cursorValue = 0L
    var userId: String? = null
    private var seq = 0L
    private val stateFlow = MutableStateFlow(LocalSyncState())
    private val latest = MutableStateFlow<Long?>(null)

    /** A write on this phone: the row is stored and marked pending, like the triggers do. */
    fun write(row: SyncRow) {
        rows[row.ref] = row
        pending[row.ref] = ++seq
        changed()
    }

    private fun changed() {
        stateFlow.value = stateFlow.value.copy(pending = pending.size)
        latest.value = pending.values.maxOrNull()
    }

    override suspend fun pendingRows(): List<PendingRow> =
        pending.map { (ref, s) -> PendingRow(rows.getValue(ref), s) }
            .sortedWith(compareBy({ TABLE_ORDER.indexOf(it.row.ref.table) }, { it.seq }))

    override suspend fun latestPending(): Long = pending.values.maxOrNull() ?: 0

    override fun observeLatestPending(): Flow<Long?> = latest

    override suspend fun clearPushed(rows: List<PendingRow>) {
        rows.forEach { p -> if ((pending[p.row.ref] ?: Long.MAX_VALUE) <= p.seq) pending.remove(p.row.ref) }
        changed()
    }

    override suspend fun cursor(): Long = cursorValue

    override suspend fun applyPage(rows: List<SyncRow>, cursor: Long): PageApplier.Counts {
        val counts = PageApplier.apply(rows, target)
        cursorValue = cursor
        changed()
        return counts
    }

    override suspend fun startAccount(userId: String): Boolean {
        if (this.userId == userId) return false
        rows.keys.forEach { pending[it] = ++seq }
        inbox.clear()
        cursorValue = 0
        stateFlow.value = LocalSyncState(pending = pending.size)
        this.userId = userId
        changed()
        return true
    }

    override suspend fun recordSuccess(at: Long) {
        stateFlow.value = stateFlow.value.copy(lastSyncedAt = at, failed = false)
    }

    override suspend fun recordFailure(code: String) {
        lastFailure = code
        stateFlow.value = stateFlow.value.copy(failed = true)
    }

    var lastFailure: String? = null

    override val state: Flow<LocalSyncState> = stateFlow.map { it }

    val target = object : ApplyTarget {
        override suspend fun version(ref: RowRef): RowVersion? = this@FakeSyncLocalStore.rows[ref]?.version

        override suspend fun write(row: SyncRow) {
            this@FakeSyncLocalStore.rows[row.ref] = row
            pending.remove(row.ref)
        }

        override suspend fun inbox(): List<SyncRow> = inbox.values.toList()

        override suspend fun putInbox(row: SyncRow) {
            inbox[row.ref] = row
        }

        override suspend fun removeInbox(ref: RowRef) {
            inbox.remove(ref)
        }
    }
}

/** The scheduler's calls, in order. */
class FakeSyncScheduler : SyncScheduler {
    val calls = mutableListOf<String>()
    override fun schedulePeriodic() { calls += "periodic" }
    override fun syncNow() { calls += "now" }
    override fun syncSoon() { calls += "soon" }
    override fun cancelAll() { calls += "cancel" }
}

// Rows for tests. Ids need not be UUIDs on the phone side.

fun program(id: String = "p1", updatedAt: Long = 1, deletedAt: Long? = null) =
    ProgramRow(ProgramEntity(id, "Upper Lower", "GYM", 0, true, updatedAt, deletedAt))

fun routine(id: String = "r1", updatedAt: Long = 1, name: String = "Pull") =
    RoutineRow(RoutineEntity(id, name, updatedAt))

fun exercise(id: String = "e1", updatedAt: Long = 1) =
    ExerciseRow(ExerciseEntity(id, "Pull-up", "pull", "REPS", null, false, false, updatedAt))

fun session(id: String = "s1", updatedAt: Long = 1, endedAt: Long? = 10, deletedAt: Long? = null) =
    SessionRow(SessionEntity(id, "r1", "p1", 0, endedAt, 0, updatedAt, deletedAt))

fun set(id: String = "x1", sessionId: String = "s1", updatedAt: Long = 1, reps: Int = 8, note: String? = null, deletedAt: Long? = null) =
    SetEntryRow(SetEntryEntity(id, sessionId, "e1", 1, reps, null, note, updatedAt, deletedAt))

fun weighIn(id: String = "w1", updatedAt: Long = 1, weightKg: Double = 80.0, deletedAt: Long? = null) =
    WeighInRow(WeighInEntity(id, 0, weightKg, updatedAt, deletedAt))
