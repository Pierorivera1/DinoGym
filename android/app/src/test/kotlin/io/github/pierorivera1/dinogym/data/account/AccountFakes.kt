package io.github.pierorivera1.dinogym.data.account

import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections

/** The session store in memory, with every saved value kept for assertions. */
class FakeSessionStore(initial: StoredSession? = null) : SessionStore {
    val state = MutableStateFlow(initial)
    val saves: MutableList<StoredSession> = Collections.synchronizedList(mutableListOf())

    override val session: Flow<StoredSession?> = state

    override suspend fun save(session: StoredSession) {
        saves += session
        state.value = session
    }

    override suspend fun clear() {
        state.value = null
    }
}

/** A clock the test moves by hand. */
class MutableClock(var millis: Long) : Clock() {
    override fun millis(): Long = millis

    override fun instant(): Instant = Instant.ofEpochMilli(millis)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}

/** The generated client against a fake server; [requests] records what reached it. */
class FakeServer(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    val api = DinoGymApi(
        "https://api.test",
        MockEngine { request ->
            requests += request
            handler(request)
        },
    )

    fun requestsTo(path: String) = requests.filter { it.url.encodedPath == path }

    companion object {
        fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

        fun MockRequestHandleScope.session(access: String, expiresAt: Long, refresh: String) = json(
            """{"userId":"u1","accessToken":"$access","accessTokenExpiresAt":$expiresAt,"refreshToken":"$refresh"}""",
        )

        fun MockRequestHandleScope.unauthorized() =
            json("""{"code":"invalid_token","message":"nope"}""", HttpStatusCode.Unauthorized)

        /** A string field of the request's JSON body. */
        fun HttpRequestData.bodyField(name: String): String =
            Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                .jsonObject[name]!!.jsonPrimitive.content
    }
}

fun storedSession(
    access: String = "access-1",
    expiresAt: Long = 1_000_000L,
    refresh: String = "refresh-1",
    pending: Boolean = false,
) = StoredSession(
    userId = "u1",
    accessToken = access,
    accessTokenExpiresAt = expiresAt,
    refreshToken = refresh,
    displayName = "Piero",
    email = "piero@example.com",
    refreshPending = pending,
)
