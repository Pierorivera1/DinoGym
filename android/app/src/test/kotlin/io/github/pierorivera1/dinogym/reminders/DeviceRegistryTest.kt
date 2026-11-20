package io.github.pierorivera1.dinogym.reminders

import io.github.pierorivera1.dinogym.data.account.FakeServer
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.bodyField
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.session
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.unauthorized
import io.github.pierorivera1.dinogym.data.account.FakeSessionStore
import io.github.pierorivera1.dinogym.data.account.MutableClock
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.storedSession
import io.github.pierorivera1.dinogym.data.local.LocalFlagDao
import io.github.pierorivera1.dinogym.data.local.LocalFlagEntity
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** `local_flag` in memory. */
class FakeLocalFlagDao : LocalFlagDao {
    val flags = MutableStateFlow(mapOf<String, String>())
    override suspend fun get(key: String): String? = flags.value[key]
    override fun observe(key: String): Flow<String?> = flags.map { it[key] }
    override suspend fun put(flag: LocalFlagEntity) {
        flags.value += flag.key to flag.value
    }
    override suspend fun delete(key: String) {
        flags.value -= key
    }
}

class DeviceRegistryTest {

    private val clock = MutableClock(1_000)
    private val store = FakeSessionStore(storedSession(expiresAt = Long.MAX_VALUE / 2))
    private val flags = FakeLocalFlagDao()
    private var token: String? = "fcm-1"

    private fun TestScope.registry(server: FakeServer) = FcmDeviceRegistry(
        server.api.devices,
        SessionManager(store, server.api.auth, clock),
        flags,
        { token },
        clock,
        backgroundScope,
    )

    /** unregister's timeout on real time: runTest's virtual clock would expire it at once. */
    private suspend fun realTime(block: suspend () -> Unit) = withContext(Dispatchers.Default) { block() }

    private fun ok() = FakeServer { respond("", HttpStatusCode.NoContent) }

    private fun FakeServer.deviceCalls() = requests.filter { it.url.encodedPath.startsWith("/devices/") }

    @Test
    fun `registers under one installation id, then not again the same day`() = runTest {
        val server = ok()
        val registry = registry(server)

        registry.register()
        registry.register()
        clock.millis += FcmDeviceRegistry.DAY_MILLIS
        registry.register()

        val calls = server.deviceCalls()
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.method == HttpMethod.Put })
        assertEquals(calls[0].url.encodedPath, calls[1].url.encodedPath)
        assertEquals("fcm-1", calls[0].bodyField("token"))
        assertEquals("Bearer access-1", calls[0].headers["Authorization"])
    }

    @Test
    fun `a new token or another account registers at once`() = runTest {
        val server = ok()
        val registry = registry(server)
        registry.register()
        registry.register(newToken = "fcm-2")
        store.state.value = storedSession(expiresAt = Long.MAX_VALUE / 2).copy(userId = "u2")
        registry.register(newToken = "fcm-2")

        assertEquals(listOf("fcm-1", "fcm-2", "fcm-2"), server.deviceCalls().map { it.bodyField("token") })
    }

    @Test
    fun `nothing is sent signed out or without a token, and a failure is retried next time`() = runTest {
        var up = false
        val server = FakeServer { if (up) respond("", HttpStatusCode.NoContent) else throw UnknownHostException() }
        val registry = registry(server)

        token = null
        registry.register()
        assertEquals(0, server.deviceCalls().size)

        token = "fcm-1"
        registry.register()
        assertNull(flags.flags.value[FcmDeviceRegistry.SENT_TOKEN])
        up = true
        registry.register()
        assertEquals("fcm-1", flags.flags.value[FcmDeviceRegistry.SENT_TOKEN])

        store.state.value = null
        registry.register(newToken = "fcm-9")
        assertEquals(2, server.deviceCalls().size)
    }

    @Test
    fun `a refused token is refreshed once and the call repeated`() = runTest {
        val server = FakeServer { request ->
            when {
                request.url.encodedPath == "/auth/refresh" -> session("access-2", 10_000_000, "refresh-2")
                request.headers["Authorization"] == "Bearer access-1" -> unauthorized()
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        registry(server).register()

        assertEquals(listOf("Bearer access-1", "Bearer access-2"), server.deviceCalls().map { it.headers["Authorization"] })
        assertEquals("fcm-1", flags.flags.value[FcmDeviceRegistry.SENT_TOKEN])
    }

    @Test
    fun `unregister deletes the same installation and forgets what was sent, even offline`() = runTest {
        var up = true
        val server = FakeServer { if (up) respond("", HttpStatusCode.NoContent) else throw UnknownHostException() }
        val registry = registry(server)
        registry.register()
        realTime { registry.unregister() }

        val (put, delete) = server.deviceCalls()
        assertEquals(HttpMethod.Delete, delete.method)
        assertEquals(put.url.encodedPath, delete.url.encodedPath)
        assertNull(flags.flags.value[FcmDeviceRegistry.SENT_TOKEN])

        up = false
        registry.register()
        realTime { registry.unregister() }
        assertNull(flags.flags.value[FcmDeviceRegistry.SENT_AT])
    }
}
