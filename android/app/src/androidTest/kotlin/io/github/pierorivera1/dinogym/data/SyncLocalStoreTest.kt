package io.github.pierorivera1.dinogym.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pierorivera1.dinogym.data.local.LocalFlagEntity
import io.github.pierorivera1.dinogym.data.local.PreferenceEntity
import io.github.pierorivera1.dinogym.data.local.SessionEntity
import io.github.pierorivera1.dinogym.data.local.SetEntryEntity
import io.github.pierorivera1.dinogym.data.local.WeighInEntity
import io.github.pierorivera1.dinogym.data.sync.RoomSyncLocalStore
import io.github.pierorivera1.dinogym.data.sync.SessionRow
import io.github.pierorivera1.dinogym.data.sync.SetEntryRow
import io.github.pierorivera1.dinogym.data.sync.WeighInRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Room side of sync (task 21): the triggers, the per-table SQL and the page transaction. CI only. */
@RunWith(AndroidJUnit4::class)
class SyncLocalStoreTest {

    private val t = TestDatabase()
    private val store = RoomSyncLocalStore(t.db)

    @After
    fun tearDown() = t.close()

    private suspend fun pendingIds() = store.pendingRows().map { it.row.ref.id }.toSet()

    @Test
    fun everyWritePath_marksTheRowPending_butLocalFlagsDoNot() = runTest {
        t.seeder.seedIfNeeded()
        val seeded = store.pendingRows().size
        assertTrue("the seed's rows are pending", seeded > 0)

        t.db.weighInDao().insert(WeighInEntity("w1", 1, 80.0, 1))
        t.db.weighInDao().setDeleted("w1", deletedAt = 2, now = 2)
        t.db.preferenceDao().put(PreferenceEntity("goal", "75", 3))
        t.db.localFlagDao().put(LocalFlagEntity("anything", "x"))

        assertEquals(seeded + 2, store.pendingRows().size)
        assertTrue(pendingIds().containsAll(listOf("w1", "goal")))
    }

    @Test
    fun clearPushed_keepsARowEditedAfterItWasRead() = runTest {
        t.db.weighInDao().insert(WeighInEntity("w1", 1, 80.0, 1))
        t.db.weighInDao().insert(WeighInEntity("w2", 1, 81.0, 1))
        val read = store.pendingRows()
        t.db.weighInDao().setDeleted("w2", deletedAt = 5, now = 5)

        store.clearPushed(read)

        assertEquals(setOf("w2"), pendingIds())
    }

    @Test
    fun applyPage_writesChildrenAfterParents_parksOrphans_andStoresTheCursor() = runTest {
        t.seeder.seedIfNeeded()
        val routine = t.db.routineDao().observeAll().first().first()
        val program = t.db.programDao().getActive()!!
        val exercise = t.db.exerciseDao().observeAll().first().first()
        val session = SessionEntity("s9", routine.id, program.id, 0, 10, 0, 7)
        val set = SetEntryEntity("x9", "s9", exercise.id, 1, 8, null, null, 7)

        val first = store.applyPage(listOf(SetEntryRow(set)), cursor = 4)
        assertEquals(1, first.waiting)
        assertNull(t.db.sessionDao().getSet("x9"))
        assertEquals(4L, store.cursor())

        store.applyPage(listOf(SessionRow(session)), cursor = 8)

        assertNotNull(t.db.sessionDao().getSet("x9"))
        assertEquals(8L, store.cursor())
        assertFalse("pulled rows are not pushed back", pendingIds().contains("x9") || pendingIds().contains("s9"))
    }

    @Test
    fun applyPage_keepsANewerLocalEdit_andIsIdempotent() = runTest {
        t.db.weighInDao().insert(WeighInEntity("w1", 1, 79.0, 10))

        store.applyPage(listOf(WeighInRow(WeighInEntity("w1", 1, 81.0, 9))), cursor = 1)
        store.applyPage(listOf(WeighInRow(WeighInEntity("w2", 1, 82.0, 9))), cursor = 2)
        store.applyPage(listOf(WeighInRow(WeighInEntity("w2", 1, 82.0, 9))), cursor = 2)

        assertEquals(79.0, t.db.weighInDao().observeAll().first().first { it.id == "w1" }.weightKg, 0.0)
        assertEquals(setOf("w1"), pendingIds())
    }

    @Test
    fun startAccount_marksEverything_forANewAccountOnly() = runTest {
        t.db.weighInDao().insert(WeighInEntity("w1", 1, 80.0, 1))
        store.clearPushed(store.pendingRows())
        store.applyPage(emptyList(), cursor = 42)

        assertTrue(store.startAccount("u1"))
        assertEquals(setOf("w1"), pendingIds())
        assertEquals(0L, store.cursor())

        store.clearPushed(store.pendingRows())
        assertFalse(store.startAccount("u1"))
        assertTrue(pendingIds().isEmpty())

        assertTrue(store.startAccount("u2"))
        assertEquals(setOf("w1"), pendingIds())
    }
}
