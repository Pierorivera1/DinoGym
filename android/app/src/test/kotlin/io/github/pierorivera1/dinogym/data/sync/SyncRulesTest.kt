package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.api.models.SyncTable
import io.github.pierorivera1.dinogym.data.local.RowVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {

    // Merge rules (decision 51)

    @Test
    fun aRowThePhoneDoesNotHave_isTaken() {
        assertTrue(MergeRules.remoteWins(null, RowVersion(5, null)))
    }

    @Test
    fun theNewerUpdatedAtWins_eitherWay() {
        assertTrue(MergeRules.remoteWins(RowVersion(5, null), RowVersion(6, null)))
        assertFalse("a newer local edit is kept, pushed or not", MergeRules.remoteWins(RowVersion(6, null), RowVersion(5, null)))
        assertFalse("an older deletion does not win", MergeRules.remoteWins(RowVersion(6, null), RowVersion(5, 5)))
    }

    @Test
    fun onATie_aDeletionWins_andNothingElseChanges() {
        assertTrue(MergeRules.remoteWins(RowVersion(5, null), RowVersion(5, 5)))
        assertFalse(MergeRules.remoteWins(RowVersion(5, 5), RowVersion(5, null)))
        assertFalse("the same row again changes nothing", MergeRules.remoteWins(RowVersion(5, null), RowVersion(5, null)))
        assertFalse(MergeRules.remoteWins(RowVersion(5, 5), RowVersion(5, 5)))
    }

    // Batching (decisions 62 and 87)

    @Test
    fun batches_holdAtMost500Rows() {
        val batches = PushBatcher.split((1..1201).toList()) { 10 }

        assertEquals(listOf(500, 500, 201), batches.map { it.size })
        assertEquals((1..1201).toList(), batches.flatten())
    }

    @Test
    fun batches_stayUnderTheByteLimit_whenNotesAreLong() {
        // 400 KB rows: two fit under 900 KB, a third does not.
        val batches = PushBatcher.split((1..5).toList()) { 400_000 }

        assertEquals(listOf(2, 2, 1), batches.map { it.size })
    }

    @Test
    fun aRowBiggerThanTheLimit_goesAlone() {
        val sizes = mapOf(1 to 10, 2 to 2_000_000, 3 to 10)

        val batches = PushBatcher.split(listOf(1, 2, 3)) { sizes.getValue(it) }

        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), batches)
    }

    @Test
    fun noRows_noBatches() {
        assertTrue(PushBatcher.split(emptyList<Int>()) { 1 }.isEmpty())
    }

    // Applying a page (decision 89)

    private fun storeWithParents() = FakeSyncLocalStore().apply {
        listOf(program(), routine(), exercise()).forEach { rows[it.ref] = it }
    }

    @Test
    fun aSetBeforeItsSessionOnTheSamePage_isWrittenAfterIt() = runTest {
        val store = storeWithParents()

        val counts = store.applyPage(listOf(set(), session()), cursor = 2)

        assertEquals(PageApplier.Counts(written = 2, skipped = 0, waiting = 0), counts)
        assertTrue(store.rows.containsKey(RowRef(SyncTable.SET_ENTRY, "x1")))
        assertTrue(store.inbox.isEmpty())
    }

    @Test
    fun aSetWhoseSessionComesOnALaterPage_waits_thenLands() = runTest {
        val store = storeWithParents()

        val first = store.applyPage(listOf(set()), cursor = 1)
        assertEquals(1, first.waiting)
        assertFalse("not written without its session", store.rows.containsKey(RowRef(SyncTable.SET_ENTRY, "x1")))
        assertEquals(1L, store.cursorValue)

        val second = store.applyPage(listOf(session()), cursor = 2)

        assertEquals(2, second.written)
        assertTrue(store.rows.containsKey(RowRef(SyncTable.SET_ENTRY, "x1")))
        assertTrue(store.inbox.isEmpty())
    }

    @Test
    fun aWaitingRow_isReplacedByANewerCopyOfIt() = runTest {
        val store = storeWithParents()
        store.applyPage(listOf(set(updatedAt = 1, reps = 5)), cursor = 1)

        store.applyPage(listOf(set(updatedAt = 2, reps = 9), session()), cursor = 2)

        assertEquals(9, (store.rows.getValue(RowRef(SyncTable.SET_ENTRY, "x1")) as SetEntryRow).entity.reps)
    }

    @Test
    fun applyingTheSamePageTwice_changesNothing() = runTest {
        val store = storeWithParents()
        val page = listOf(session(updatedAt = 3), set(updatedAt = 3), weighIn(updatedAt = 3))
        store.applyPage(page, cursor = 3)
        val before = store.rows.toMap()

        val again = store.applyPage(page, cursor = 3)

        assertEquals(PageApplier.Counts(written = 0, skipped = 3, waiting = 0), again)
        assertEquals(before, store.rows)
    }

    @Test
    fun aNewerUnpushedLocalEdit_isKept_andStaysPending() = runTest {
        val store = storeWithParents()
        store.write(weighIn(updatedAt = 10, weightKg = 79.0))

        store.applyPage(listOf(weighIn(updatedAt = 9, weightKg = 81.0)), cursor = 1)

        val kept = store.rows.getValue(RowRef(SyncTable.WEIGH_IN, "w1")) as WeighInRow
        assertEquals(79.0, kept.entity.weightKg, 0.0)
        assertTrue(store.pending.containsKey(kept.ref))
    }

    @Test
    fun aNewerPulledRow_replacesAnOlderPendingOne_andClearsItsPendingMark() = runTest {
        val store = storeWithParents()
        store.write(weighIn(updatedAt = 5, weightKg = 79.0))

        store.applyPage(listOf(weighIn(updatedAt = 6, weightKg = 81.0)), cursor = 1)

        val row = store.rows.getValue(RowRef(SyncTable.WEIGH_IN, "w1")) as WeighInRow
        assertEquals(81.0, row.entity.weightKg, 0.0)
        assertFalse("the server has newer; nothing to push", store.pending.containsKey(row.ref))
    }

    @Test
    fun aDeletionWithTheSameTime_winsOverALocalLiveRow() = runTest {
        val store = storeWithParents()
        store.rows[weighIn().ref] = weighIn(updatedAt = 5)

        store.applyPage(listOf(weighIn(updatedAt = 5, deletedAt = 5)), cursor = 1)

        assertEquals(5L, store.rows.getValue(weighIn().ref).version.deletedAt)
    }

    // Wire mapping

    @Test
    fun rowsSurviveTheTripThroughAChangeSet() {
        val rows = listOf(program(), routine(), exercise(), session(), set(note = "felt easy"), weighIn())

        assertEquals(rows, rows.toChangeSet().toRows())
    }

    @Test
    fun aChangeSetWithOneTable_leavesTheOthersOut() {
        val changes = listOf(weighIn()).toChangeSet()

        assertEquals(1, changes.weighIns!!.size)
        assertEquals(null, changes.sessions)
    }
}
