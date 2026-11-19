package io.github.pierorivera1.dinogym.reminders

import io.github.pierorivera1.dinogym.api.apis.DevicesApi
import io.github.pierorivera1.dinogym.api.infrastructure.HttpResponse
import io.github.pierorivera1.dinogym.api.models.DeviceRegistration
import io.github.pierorivera1.dinogym.data.account.AccessTokenResult
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.local.LocalFlagDao
import io.github.pierorivera1.dinogym.data.local.LocalFlagEntity
import io.github.pierorivera1.dinogym.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** The phone's FCM token. An interface so the registry is tested without Firebase. */
fun interface PushTokens {
    /** The current token, or null when Firebase is not set up in this build or not reachable. */
    suspend fun current(): String?
}

/** Registers this phone for reminders (decision 106). */
interface DeviceRegistry {
    /**
     * Sends `PUT /devices/{installationId}` when signed in. Skipped when the same token was
     * sent for the same account less than a day ago, unless [newToken] is a token FCM just
     * handed out.
     */
    suspend fun register(newToken: String? = null)

    /** Sends `DELETE /devices/{installationId}` before sign-out. Best effort: never throws. */
    suspend fun unregister()
}

/**
 * The real [DeviceRegistry]. The installation id is a random UUID in `local_flag`, made once
 * per install, so a refreshed token replaces the old one on the server instead of adding one.
 * What was last sent (account, token, time) is kept there too, so app starts do not call the
 * API every time. [start] follows the account: each sign-in registers.
 */
@Singleton
class FcmDeviceRegistry @Inject constructor(
    private val api: DevicesApi,
    private val sessions: SessionManager,
    private val flags: LocalFlagDao,
    private val tokens: PushTokens,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : DeviceRegistry {

    private val mutex = Mutex()

    fun start() {
        scope.launch {
            sessions.userId.collectLatest { userId -> if (userId != null) register() }
        }
    }

    override suspend fun register(newToken: String?) = mutex.withLock {
        val userId = sessions.userId.first() ?: return@withLock
        val token = newToken ?: tokens.current() ?: return@withLock
        val sentRecently = flags.get(SENT_USER) == userId &&
            flags.get(SENT_TOKEN) == token &&
            clock.millis() - (flags.get(SENT_AT)?.toLongOrNull() ?: 0L) < DAY_MILLIS
        if (sentRecently) return@withLock
        val id = installationId()
        val sent = authorized { api.registerDevice(id, DeviceRegistration(token)) }
        if (sent) {
            flags.put(LocalFlagEntity(SENT_USER, userId))
            flags.put(LocalFlagEntity(SENT_TOKEN, token))
            flags.put(LocalFlagEntity(SENT_AT, clock.millis().toString()))
        }
    }

    override suspend fun unregister() {
        mutex.withLock {
            val id = flags.get(INSTALLATION_ID)
            if (id != null) {
                // Sign-out must not wait on a slow network. If this fails, the server's 60-day
                // TTL removes the device, and the messaging service ignores messages signed out.
                withTimeoutOrNull(UNREGISTER_TIMEOUT_MILLIS) { authorized { api.unregisterDevice(id) } }
            }
            listOf(SENT_USER, SENT_TOKEN, SENT_AT).forEach { flags.delete(it) }
        }
    }

    private suspend fun installationId(): String =
        flags.get(INSTALLATION_ID) ?: UUID.randomUUID().toString().also { flags.put(LocalFlagEntity(INSTALLATION_ID, it)) }

    /**
     * Runs [call] with a valid access token, refreshing once on a 401 (decision 91's rule).
     * True when the API answered 2xx; false when offline, signed out or refused.
     */
    private suspend fun authorized(call: suspend () -> HttpResponse<Unit>): Boolean {
        val first = (sessions.accessToken() as? AccessTokenResult.Token)?.value ?: return false
        var response = send(first, call) ?: return false
        if (response.status == UNAUTHORIZED) {
            val second = (sessions.accessToken(rejected = first) as? AccessTokenResult.Token)?.value ?: return false
            response = send(second, call) ?: return false
        }
        return response.success
    }

    private suspend fun send(token: String, call: suspend () -> HttpResponse<Unit>): HttpResponse<Unit>? = try {
        api.setBearerToken(token)
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    }

    companion object {
        const val INSTALLATION_ID = "device_installation_id"
        const val SENT_USER = "device_sent_user"
        const val SENT_TOKEN = "device_sent_token"
        const val SENT_AT = "device_sent_at"
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val UNREGISTER_TIMEOUT_MILLIS = 5_000L
        private const val UNAUTHORIZED = 401
    }
}
