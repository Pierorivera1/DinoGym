package io.github.pierorivera1.dinogym.api

import io.github.pierorivera1.dinogym.api.models.ChangeSet
import io.github.pierorivera1.dinogym.api.models.PushResult
import io.github.pierorivera1.dinogym.api.models.SyncTable
import io.github.pierorivera1.dinogym.api.models.WeighIn
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calls the generated client against a fake server, so the contract and the JSON setup are checked together. */
class DinoGymApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): DinoGymApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return DinoGymApi("https://api.test", engine)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyJson() =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    @Test
    fun push_sendsRowsWithBearerToken_andReadsResults() = runTest {
        val api = api {
            json("""{"results":[{"table":"weigh_in","id":"w1","status":"accepted","changeNumber":7}]}""")
        }
        api.setAccessToken("token-1")

        val deleted = WeighIn(id = "w1", measuredAt = 1, weightKg = 80.5, updatedAt = 2, deletedAt = 3)
        val live = WeighIn(id = "w2", measuredAt = 1, weightKg = 81.0, updatedAt = 2)
        val response = api.sync.pushChanges(ChangeSet(weighIns = listOf(deleted, live)))

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/sync/push", request.url.encodedPath)
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])

        val rows = request.bodyJson()["weighIns"]!!.jsonArray
        assertEquals(3L, rows[0].jsonObject["deletedAt"]!!.jsonPrimitive.long)
        assertFalse("a live row leaves deletedAt out", "deletedAt" in rows[1].jsonObject)

        assertTrue(response.success)
        val result = response.body().results.single()
        assertEquals(SyncTable.WEIGH_IN, result.table)
        assertEquals(PushResult.Status.ACCEPTED, result.status)
        assertEquals(7L, result.changeNumber)
    }

    @Test
    fun pull_passesCursor_andIgnoresFieldsTheAppDoesNotKnow() = runTest {
        val api = api {
            json(
                """{"changes":{"preferences":[{"key":"goal_kg","value":"75","updatedAt":5,"addedLater":true}]},
                   "cursor":12,"hasMore":false,"addedLater":1}""",
            )
        }

        val page = api.sync.pullChanges(since = 4, limit = 100).body()

        assertEquals("4", requests.single().url.parameters["since"])
        assertEquals("100", requests.single().url.parameters["limit"])
        assertEquals(12L, page.cursor)
        assertFalse(page.hasMore)
        val pref = page.changes.preferences!!.single()
        assertEquals("goal_kg", pref.key)
        assertNull(pref.deletedAt)
    }

    @Test
    fun anExpiredToken_isAReadableFailure_notAnException() = runTest {
        val api = api {
            json("""{"code":"invalid_token","message":"expired"}""", HttpStatusCode.Unauthorized)
        }

        val response = api.sync.pullChanges(since = 0)

        assertFalse(response.success)
        assertEquals(401, response.status)
    }

    @Test
    fun health_needsNoToken() = runTest {
        val api = api { json("""{"status":"ok"}""") }

        assertTrue(api.health.getHealth().success)
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }
}
