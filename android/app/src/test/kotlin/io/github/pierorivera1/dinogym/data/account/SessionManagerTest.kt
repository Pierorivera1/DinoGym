package io.github.pierorivera1.dinogym.data.account

import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.bodyField
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.json
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.session
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.unauthorized
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SessionManagerTest {

    private val clock = MutableClock(0)

    private fun manager(store: SessionStore, server: FakeServer) = SessionManager(store, server.api.auth, clock)

    @Test
    fun signIn_postsTheIdToken_andStoresTheSessionWithNameAndEmail() = runTest {
        val server = FakeServer { session("a1", 900_000, "r1") }
        val store = FakeSessionStore()

        val result = manager(store, server).signIn("google-id-token", "Piero", "piero@example.com")

        assertEquals(SignInResult.Success, result)
        val request = server.requestsTo("/auth/google").single()
        assertEquals("google-id-token", request.bodyField("idToken"))
        assertEquals(storedSession(access = "a1", expiresAt = 900_000, refresh = "r1"), store.state.value)
    }

    @Test
    fun signIn_offlineOrRefused_storesNothing() = runTest {
        val store = FakeSessionStore()

        val offline = manager(store, FakeServer { throw UnknownHostException("no network") }).signIn("t", "P", "e")
        val refused = manager(store, FakeServer { unauthorized() }).signIn("t", "P", "e")
        val broken = manager(store, FakeServer { json("""{"code":"internal","message":"x"}""", HttpStatusCode.InternalServerError) })
            .signIn("t", "P", "e")

        assertEquals(SignInResult.Offline, offline)
        assertEquals(SignInResult.Failed, refused)
        assertEquals(SignInResult.Failed, broken)
        assertNull(store.state.value)
    }

    @Test
    fun aTokenThatIsStillValid_isReturnedWithoutCallingTheServer() = runTest {
        val server = FakeServer { error("no request expected") }
        clock.millis = 1_000_000 - SessionManager.EXPIRY_SKEW_MILLIS - 1

        val result = manager(FakeSessionStore(storedSession()), server).accessToken()

        assertEquals(AccessTokenResult.Token("access-1"), result)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aTokenTheApiRejected_isRefreshed_evenIfTheClockSaysItIsValid() = runTest {
        val server = FakeServer { session("access-2", 2_000_000, "refresh-2") }
        val store = FakeSessionStore(storedSession())

        val result = manager(store, server).accessToken(rejected = "access-1")

        assertEquals(AccessTokenResult.Token("access-2"), result)
        assertEquals(1, server.requestsTo("/auth/refresh").size)
    }

    @Test
    fun aRejectedTokenThatWasAlreadyReplaced_isNotRefreshedAgain() = runTest {
        val server = FakeServer { error("no request expected") }
        val store = FakeSessionStore(storedSession(access = "access-2"))

        val result = manager(store, server).accessToken(rejected = "access-1")

        assertEquals(AccessTokenResult.Token("access-2"), result)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aTokenAboutToExpire_isRefreshed_andTheNewPairIsStored() = runTest {
        val server = FakeServer { session("access-2", 2_000_000, "refresh-2") }
        val store = FakeSessionStore(storedSession())
        // Inside the one-minute skew: still valid on paper, refreshed anyway.
        clock.millis = 1_000_000 - SessionManager.EXPIRY_SKEW_MILLIS

        val result = manager(store, server).accessToken()

        assertEquals(AccessTokenResult.Token("access-2"), result)
        assertEquals("refresh-1", server.requestsTo("/auth/refresh").single().bodyField("refreshToken"))
        val stored = store.state.value!!
        assertEquals("refresh-2", stored.refreshToken)
        assertFalse(stored.refreshPending)
        assertEquals("the name and email survive a refresh", "Piero", stored.displayName)
        assertTrue("marked pending before it was sent", store.saves.first().refreshPending)
    }

    @Test
    fun twoCallersAtOnce_causeOneRefresh() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val server = FakeServer {
            started.complete(Unit)
            release.await()
            session("access-2", 2_000_000, "refresh-2")
        }
        val manager = manager(FakeSessionStore(storedSession()), server)
        clock.millis = 1_000_000

        val first = async { manager.accessToken() }
        val second = async { manager.accessToken() }
        started.await()
        release.complete(Unit)

        assertEquals(AccessTokenResult.Token("access-2"), first.await())
        assertEquals(AccessTokenResult.Token("access-2"), second.await())
        assertEquals(1, server.requestsTo("/auth/refresh").size)
    }

    @Test
    fun aRefusedRefresh_signsOutLocally_andIsNotRetried() = runTest {
        val server = FakeServer { unauthorized() }
        val store = FakeSessionStore(storedSession())
        val manager = manager(store, server)
        clock.millis = 1_000_000

        assertEquals(AccessTokenResult.SignedOut, manager.accessToken())
        assertEquals(AccessTokenResult.SignedOut, manager.accessToken())

        assertNull(store.state.value)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun noNetwork_keepsTheSession_andReportsOffline() = runTest {
        val server = FakeServer { throw UnknownHostException("airplane mode") }
        val store = FakeSessionStore(storedSession())
        clock.millis = 1_000_000

        val result = manager(store, server).accessToken()

        assertEquals(AccessTokenResult.Offline, result)
        assertEquals(storedSession(), store.state.value)
    }

    @Test
    fun throttled_keepsTheSession_andReportsOffline() = runTest {
        val server = FakeServer { json("""{"message":"Too Many Requests"}""", HttpStatusCode.TooManyRequests) }
        val store = FakeSessionStore(storedSession())
        clock.millis = 1_000_000

        assertEquals(AccessTokenResult.Offline, manager(store, server).accessToken())
        assertEquals(storedSession(), store.state.value)
    }

    @Test
    fun aRefreshThatMayHaveReachedTheServer_isNeverSentAgain() = runTest {
        // A read timeout: the request was sent, the answer never came.
        val timedOut = FakeServer { throw SocketTimeoutException("read timed out") }
        val serverError = FakeServer { json("""{"code":"internal","message":"x"}""", HttpStatusCode.InternalServerError) }

        for (server in listOf(timedOut, serverError)) {
            val store = FakeSessionStore(storedSession())
            val manager = manager(store, server)
            clock.millis = 1_000_000

            assertEquals(AccessTokenResult.SignedOut, manager.accessToken())
            assertEquals(AccessTokenResult.SignedOut, manager.accessToken())

            assertNull(store.state.value)
            assertEquals("refresh-1 went out once", 1, server.requestsTo("/auth/refresh").size)
        }
    }

    @Test
    fun aPendingRefreshFromBeforeTheAppStopped_isNeverSent() = runTest {
        val server = FakeServer { error("no request expected") }
        val store = FakeSessionStore(storedSession(pending = true))

        assertEquals(AccessTokenResult.SignedOut, manager(store, server).accessToken())

        assertNull(store.state.value)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun signOut_clearsTheSession() = runTest {
        val store = FakeSessionStore(storedSession())
        val manager = manager(store, FakeServer { error("no request expected") })

        manager.signOut()

        assertNull(store.state.value)
        assertEquals(AccessTokenResult.SignedOut, manager.accessToken())
    }
}
