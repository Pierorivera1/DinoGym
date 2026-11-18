package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.models.ChangeSet
import io.github.pierorivera1.dinogym.api.models.PullResponse
import io.github.pierorivera1.dinogym.api.models.PushResponse
import io.github.pierorivera1.dinogym.api.models.PushResult
import io.github.pierorivera1.dinogym.api.models.SyncTable
import io.github.pierorivera1.dinogym.data.account.FakeServer
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.bodyField
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.json
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.session
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.unauthorized
import io.github.pierorivera1.dinogym.data.account.FakeSessionStore
import io.github.pierorivera1.dinogym.data.account.MutableClock
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.storedSession
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class SyncEngineTest {

    private val clock = MutableClock(0)
    private val local = FakeSyncLocalStore().apply {
        listOf(program(), routine(), exercise()).forEach { rows[it.ref] = it }
    }
    private val sessionStore = FakeSessionStore(storedSession())

    private fun engine(server: FakeServer) =
        SyncEngine(server.api.sync, SessionManager(sessionStore, server.api.auth, clock), local, clock)

    // Wire helpers

    private fun HttpRequestData.pushed(): ChangeSet =
        DinoGymApi.JSON.decodeFromString((body as OutgoingContent.ByteArrayContent).bytes().decodeToString())

    private fun ChangeSet.size() = listOf(programs, cycleSteps, routines, exercises, routineExercises, sessions, setEntries, weighIns, preferences)
        .sumOf { it?.size ?: 0 }

    /** Answers every pushed row with [status] (or by id from [byId]). */
    private fun MockRequestHandleScope.answerPush(
        request: HttpRequestData,
        byId: Map<String, PushResult.Status> = emptyMap(),
        status: PushResult.Status = PushResult.Status.ACCEPTED,
    ): HttpResponseData {
        val results = request.pushed().toRows().map {
            val s = byId[it.ref.id] ?: status
            PushResult(it.ref.table, it.ref.id, s, reason = if (s == PushResult.Status.REJECTED) "updated_at_in_future" else null)
        }
        return json(DinoGymApi.JSON.encodeToString(PushResponse(results)))
    }

    private fun MockRequestHandleScope.page(rows: List<SyncRow> = emptyList(), cursor: Long = 0, hasMore: Boolean = false) =
        json(DinoGymApi.JSON.encodeToString(PullResponse(rows.toChangeSet(), cursor, hasMore)))

    private val HttpRequestData.path get() = url.encodedPath

    // Push

    @Test
    fun push_acceptedAndStaleClear_rejectedStaysPending_withoutLooping() = runTest {
        local.write(weighIn("w1", updatedAt = 1))
        local.write(weighIn("w2", updatedAt = 1))
        local.write(weighIn("w3", updatedAt = 1))
        val server = FakeServer { request ->
            when (request.path) {
                "/sync/push" -> answerPush(
                    request,
                    byId = mapOf("w2" to PushResult.Status.STALE, "w3" to PushResult.Status.REJECTED),
                )
                else -> page()
            }
        }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Synced(moreWaiting = false), outcome)
        assertEquals(1, server.requestsTo("/sync/push").size)
        assertEquals(setOf(RowRef(SyncTable.WEIGH_IN, "w3")), local.pending.keys)
        assertEquals("the access token is sent", "Bearer access-1", server.requests.first().headers[HttpHeaders.Authorization])
    }

    @Test
    fun push_splitsIntoBatchesOf500() = runTest {
        repeat(1_200) { local.write(weighIn("w$it")) }
        val server = FakeServer { request -> if (request.path == "/sync/push") answerPush(request) else page() }

        engine(server).sync()

        assertEquals(listOf(500, 500, 200), server.requestsTo("/sync/push").map { it.pushed().size() })
        assertTrue(local.pending.isEmpty())
    }

    @Test
    fun push_sendsParentsBeforeChildren() = runTest {
        local.write(set())
        local.write(session())
        val server = FakeServer { request -> if (request.path == "/sync/push") answerPush(request) else page() }

        engine(server).sync()

        val sent = server.requestsTo("/sync/push").single().pushed().toRows().map { it.ref.table }
        assertEquals(listOf(SyncTable.SESSION, SyncTable.SET_ENTRY), sent)
    }

    @Test
    fun aBodyTooLarge_splitsTheBatchInHalf() = runTest {
        repeat(4) { local.write(weighIn("w$it")) }
        val server = FakeServer { request ->
            when {
                request.path != "/sync/push" -> page()
                request.pushed().size() > 2 -> json("""{"code":"body_too_large","message":"x"}""", HttpStatusCode.PayloadTooLarge)
                else -> answerPush(request)
            }
        }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Synced(false), outcome)
        assertEquals(listOf(4, 2, 2), server.requestsTo("/sync/push").map { it.pushed().size() })
        assertTrue(local.pending.isEmpty())
    }

    @Test
    fun anEditDuringTheRun_staysPending_andAsksForAnotherRun() = runTest {
        local.write(weighIn("w1", updatedAt = 1))
        val server = FakeServer { request ->
            if (request.path == "/sync/push") {
                // The user logs a new weight while the push is on the wire.
                local.write(weighIn("w1", updatedAt = 2))
                answerPush(request)
            } else {
                page()
            }
        }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Synced(moreWaiting = true), outcome)
        assertTrue(local.pending.containsKey(RowRef(SyncTable.WEIGH_IN, "w1")))
    }

    // Errors

    @Test
    fun a401_refreshesOnce_andRetriesWithTheNewToken() = runTest {
        local.write(weighIn())
        val server = FakeServer { request ->
            when {
                request.path == "/auth/refresh" -> session("access-2", 9_000_000, "refresh-2")
                request.headers[HttpHeaders.Authorization] == "Bearer access-1" -> unauthorized()
                request.path == "/sync/push" -> answerPush(request)
                else -> page()
            }
        }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Synced(false), outcome)
        assertEquals("refresh-1", server.requestsTo("/auth/refresh").single().bodyField("refreshToken"))
        assertEquals(listOf("Bearer access-1", "Bearer access-2"), server.requestsTo("/sync/push").map { it.headers[HttpHeaders.Authorization] })
        assertEquals("the pull uses the new token too", "Bearer access-2", server.requestsTo("/sync/pull").single().headers[HttpHeaders.Authorization])
        assertTrue(local.pending.isEmpty())
    }

    @Test
    fun a401AfterTheRefresh_stops_withoutASecondRefresh() = runTest {
        local.write(weighIn())
        val server = FakeServer { request ->
            if (request.path == "/auth/refresh") session("access-2", 9_000_000, "refresh-2") else unauthorized()
        }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Failed("unauthorized"), outcome)
        assertEquals(1, server.requestsTo("/auth/refresh").size)
        assertEquals(2, server.requestsTo("/sync/push").size)
        assertFalse(local.pending.isEmpty())
        assertEquals("unauthorized", local.lastFailure)
    }

    @Test
    fun aServerError_isRetriedLater_andKeepsEverythingPending() = runTest {
        local.write(weighIn())
        val server = FakeServer { json("""{"code":"internal","message":"x"}""", HttpStatusCode.InternalServerError) }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.RetryLater, outcome)
        assertEquals(1, local.pending.size)
        assertTrue(server.requestsTo("/sync/pull").isEmpty())
        assertNotNull(local.lastFailure)
    }

    @Test
    fun aBadRequest_isReported_andNotRetried() = runTest {
        local.write(weighIn())
        val server = FakeServer { json("""{"code":"bad_request","message":"x"}""", HttpStatusCode.BadRequest) }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Failed("push_400"), outcome)
        assertEquals(1, server.requestsTo("/sync/push").size)
        assertTrue(server.requestsTo("/sync/pull").isEmpty())
    }

    @Test
    fun noNetwork_isOffline_andNothingIsLost() = runTest {
        local.write(weighIn())
        val server = FakeServer { throw UnknownHostException("airplane mode") }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Offline, outcome)
        assertEquals(1, local.pending.size)
        assertNull("offline is not an error", local.lastFailure)
    }

    @Test
    fun signedOut_sendsNothing() = runTest {
        sessionStore.state.value = null
        local.write(weighIn())
        val server = FakeServer { error("no request expected") }

        assertEquals(SyncOutcome.SignedOut, engine(server).sync())
        assertTrue(server.requests.isEmpty())
    }

    // Pull

    @Test
    fun pull_pagesUntilHasMoreIsFalse_andStoresTheCursor() = runTest {
        local.cursorValue = 3
        val server = FakeServer { request ->
            when (request.url.parameters["since"]) {
                "3" -> page(listOf(weighIn("w1")), cursor = 5, hasMore = true)
                "5" -> page(listOf(weighIn("w2")), cursor = 9, hasMore = false)
                else -> error("unexpected since")
            }
        }

        val outcome = engine(server).sync()

        assertEquals(SyncOutcome.Synced(false), outcome)
        val pulls = server.requestsTo("/sync/pull")
        assertEquals(listOf("3", "5"), pulls.map { it.url.parameters["since"] })
        assertEquals(listOf("1000", "1000"), pulls.map { it.url.parameters["limit"] })
        assertEquals(9L, local.cursorValue)
        assertTrue(local.rows.containsKey(RowRef(SyncTable.WEIGH_IN, "w2")))
        assertTrue("pulled rows are not pushed back", local.pending.isEmpty())
    }

    @Test
    fun pull_aSetOnPage1WhoseSessionIsOnPage2_landsWithoutCrashing() = runTest {
        val server = FakeServer { request ->
            when (request.url.parameters["since"]) {
                "0" -> page(listOf(set()), cursor = 6, hasMore = true)
                else -> page(listOf(session(updatedAt = 7)), cursor = 7, hasMore = false)
            }
        }

        engine(server).sync()

        assertTrue(local.rows.containsKey(RowRef(SyncTable.SET_ENTRY, "x1")))
        assertTrue(local.inbox.isEmpty())
    }

    @Test
    fun pull_ourOwnPushComingBack_changesNothing() = runTest {
        val mine = weighIn("w1", updatedAt = 4)
        local.write(mine)
        val server = FakeServer { request ->
            if (request.path == "/sync/push") answerPush(request) else page(listOf(mine), cursor = 1)
        }

        engine(server).sync()

        assertEquals(mine, local.rows[mine.ref])
        assertTrue(local.pending.isEmpty())
    }

    @Test
    fun aServerThatSaysMoreWithoutMovingTheCursor_doesNotLoop() = runTest {
        val server = FakeServer { page(cursor = 0, hasMore = true) }

        engine(server).sync()

        assertEquals(1, server.requestsTo("/sync/pull").size)
    }

    @Test
    fun aSuccessfulRun_isRecorded() = runTest {
        clock.millis = 1_234
        val server = FakeServer { page() }

        engine(server).sync()

        assertEquals(1_234L, local.state.first().lastSyncedAt)
    }
}
