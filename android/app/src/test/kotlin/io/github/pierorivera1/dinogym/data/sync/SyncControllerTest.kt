package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.data.account.FakeServer
import io.github.pierorivera1.dinogym.data.account.FakeSessionStore
import io.github.pierorivera1.dinogym.data.account.MutableClock
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.storedSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncControllerTest {

    private val store = FakeSessionStore()
    private val local = FakeSyncLocalStore()
    private val scheduler = FakeSyncScheduler()

    /** The phone's history before anyone signs in: written locally, so already pending. */
    private val history = listOf(program(), routine(), exercise(), session(), set(), weighIn())

    private fun TestScope.startController() {
        val sessions = SessionManager(store, FakeServer { error("no request expected") }.api.auth, MutableClock(0))
        val controller = SyncController(sessions, local, scheduler, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.follow() }
    }

    private fun signIn(userId: String) {
        store.state.value = storedSession().copy(userId = userId)
    }

    @Test
    fun signedOut_nothingIsScheduled() = runTest {
        startController()

        assertEquals(listOf("cancel"), scheduler.calls)
    }

    @Test
    fun firstSignIn_uploadsTheWholeHistory_fromCursorZero() = runTest {
        history.forEach { local.rows[it.ref] = it }
        local.cursorValue = 42
        startController()
        scheduler.calls.clear()

        signIn("u1")

        assertEquals(history.map { it.ref }.toSet(), local.pending.keys)
        assertEquals(0L, local.cursorValue)
        assertEquals(listOf("periodic", "now", "soon"), scheduler.calls)
    }

    @Test
    fun theSameAccountAgain_keepsPendingChangesAndTheCursor() = runTest {
        local.userId = "u1"
        local.cursorValue = 42
        local.write(weighIn())
        startController()

        signIn("u1")

        assertEquals(42L, local.cursorValue)
        assertEquals(setOf(weighIn().ref), local.pending.keys)
        assertEquals("no full upload, but the waiting change goes up soon", listOf("cancel", "periodic", "soon"), scheduler.calls)
    }

    @Test
    fun aDifferentAccount_getsThePhonesDataToo_andPullsFromZero() = runTest {
        local.userId = "u1"
        local.cursorValue = 42
        history.forEach { local.rows[it.ref] = it }
        startController()

        signIn("u2")

        assertEquals(history.size, local.pending.size)
        assertEquals(0L, local.cursorValue)
        assertEquals("u2", local.userId)
        assertTrue(scheduler.calls.contains("now"))
    }

    @Test
    fun signOut_cancelsEverything_andKeepsPendingChanges() = runTest {
        signIn("u1")
        startController()
        local.write(weighIn())
        scheduler.calls.clear()

        store.state.value = null

        assertEquals(listOf("cancel"), scheduler.calls)
        assertEquals(1, local.pending.size)
    }

    @Test
    fun aChangeWhileSignedIn_schedulesASyncSoon_andNotWhileSignedOut() = runTest {
        signIn("u1")
        startController()
        scheduler.calls.clear()

        local.write(weighIn("w9"))
        assertEquals(listOf("soon"), scheduler.calls)

        scheduler.calls.clear()
        store.state.value = null
        local.write(weighIn("w10"))
        assertEquals(listOf("cancel"), scheduler.calls)
    }
}
