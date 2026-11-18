package io.github.pierorivera1.dinogym.data.account

import io.github.pierorivera1.dinogym.api.apis.AuthApi
import io.github.pierorivera1.dinogym.api.models.GoogleSignInRequest
import io.github.pierorivera1.dinogym.api.models.RefreshRequest
import io.github.pierorivera1.dinogym.api.models.Session
import io.ktor.client.network.sockets.ConnectTimeoutException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException

/** The answer to "give me an access token" (for sync, task 21). */
sealed interface AccessTokenResult {
    data class Token(val value: String) : AccessTokenResult

    /** Nobody is signed in, or the session just ended (the refresh was refused or may be spent). */
    data object SignedOut : AccessTokenResult

    /** The server could not be reached. The session is kept; try again later. */
    data object Offline : AccessTokenResult
}

/** How a sign-in with a Google ID token ended. */
sealed interface SignInResult {
    data object Success : SignInResult

    /** No connection. Nothing was stored. */
    data object Offline : SignInResult

    /** The server answered with an error (a refused token, a server fault). Nothing was stored. */
    data object Failed : SignInResult
}

/**
 * Owns the signed-in session (task 20, decision 81): signs in with a Google ID token, hands
 * out a valid access token, refreshes it, and signs out. Nothing else in the app needs it,
 * so everything keeps working signed out (offline first).
 *
 * The refresh rules follow the backend's (decision 79), where a refresh token is single use
 * and a reused one revokes the whole session family:
 * - **Single flight.** One [Mutex] covers every read and write of the session, so two callers
 *   that find the token expired cause one `/auth/refresh`; the second sees the new token.
 * - **Never sent twice.** A refresh token is marked pending before it is sent. Once the
 *   request may have reached the server (a reply of any kind, a timeout, a dropped
 *   connection, or the app dying mid-request) that token is never sent again: a 401 or any
 *   doubt ends the session locally. Only failures that prove the request never left the
 *   phone (no DNS, no route, connection refused, connect timeout, TLS handshake) keep the
 *   session and report [AccessTokenResult.Offline]. A 429 from API Gateway's throttle
 *   (decision 67) also never reached the code, so it counts as offline.
 * - Signing out locally deletes the tokens, name and email, never workout data.
 */
@Singleton
class SessionManager @Inject constructor(
    private val store: SessionStore,
    private val auth: AuthApi,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Who is signed in, or null when signed out. */
    val account: Flow<Account?> = store.session
        .map { it?.let { session -> Account(session.displayName, session.email) } }
        .distinctUntilChanged()

    /** The signed-in DinoGym user id, or null when signed out. Sync follows it (task 21). */
    val userId: Flow<String?> = store.session.map { it?.userId }.distinctUntilChanged()

    /**
     * Exchanges a Google [idToken] for a DinoGym session and stores it with the [displayName]
     * and [email] from Google. Safe to retry: every sign-in starts a new session family.
     */
    suspend fun signIn(idToken: String, displayName: String, email: String): SignInResult {
        val session = try {
            val response = auth.signInWithGoogle(GoogleSignInRequest(idToken))
            if (!response.success) return SignInResult.Failed
            response.body()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return SignInResult.Offline
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // An unreadable body: the server is not speaking the contract.
            return SignInResult.Failed
        }
        mutex.withLock { store.save(session.toStored(displayName, email)) }
        return SignInResult.Success
    }

    /**
     * A valid access token, refreshing it first when it expires within [EXPIRY_SKEW_MILLIS].
     * Callers that get [AccessTokenResult.SignedOut] stop syncing; the ACCOUNT card already
     * shows the signed-out state, since the session is gone from the store.
     *
     * [rejected] is a token the API just answered 401 to (task 21): if it is still the stored
     * one, it is refreshed even though the phone's clock says it is valid. If another caller
     * already refreshed it, the newer token comes back and nothing is sent, so the
     * single-flight and never-twice rules still hold.
     */
    suspend fun accessToken(rejected: String? = null): AccessTokenResult = mutex.withLock {
        val session = store.read() ?: return AccessTokenResult.SignedOut
        when {
            // A refresh was sent and the app stopped before storing its answer.
            session.refreshPending -> endSession()
            session.accessToken != rejected &&
                clock.millis() < session.accessTokenExpiresAt - EXPIRY_SKEW_MILLIS ->
                AccessTokenResult.Token(session.accessToken)
            else -> refresh(session)
        }
    }

    /** Signs out on this phone. Workouts and every other table are left alone. */
    suspend fun signOut() {
        mutex.withLock { store.clear() }
    }

    /** Runs under [mutex]. */
    private suspend fun refresh(session: StoredSession): AccessTokenResult {
        store.save(session.copy(refreshPending = true))
        val fresh = try {
            val response = auth.refreshSession(RefreshRequest(session.refreshToken))
            when {
                response.success -> response.body()
                response.status == TOO_MANY_REQUESTS -> return keep(session)
                // 401 (refused, expired, reused or revoked), 400, or a 5xx after which the
                // token may be spent. In every case it is never sent again.
                else -> return endSession()
            }
        } catch (e: CancellationException) {
            // The caller gave up while the request may be on the wire: treat the token as spent.
            withContext(NonCancellable) { store.clear() }
            throw e
        } catch (e: IOException) {
            return if (e.neverLeftThePhone()) keep(session) else endSession()
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // A 200 whose body cannot be read: the old token is spent and the new one is lost.
            return endSession()
        }
        val stored = fresh.toStored(session.displayName, session.email)
        store.save(stored)
        return AccessTokenResult.Token(stored.accessToken)
    }

    private suspend fun keep(session: StoredSession): AccessTokenResult {
        store.save(session.copy(refreshPending = false))
        return AccessTokenResult.Offline
    }

    private suspend fun endSession(): AccessTokenResult {
        store.clear()
        return AccessTokenResult.SignedOut
    }

    private fun Session.toStored(displayName: String, email: String) = StoredSession(
        userId = userId,
        accessToken = accessToken,
        accessTokenExpiresAt = accessTokenExpiresAt,
        refreshToken = refreshToken,
        displayName = displayName,
        email = email,
    )

    private fun IOException.neverLeftThePhone(): Boolean =
        this is UnknownHostException ||
            this is ConnectException ||
            this is NoRouteToHostException ||
            this is ConnectTimeoutException ||
            this is SSLHandshakeException

    companion object {
        /** Refresh a minute early, so a token does not expire on its way to the server. */
        const val EXPIRY_SKEW_MILLIS = 60_000L
        private const val TOO_MANY_REQUESTS = 429
    }
}
