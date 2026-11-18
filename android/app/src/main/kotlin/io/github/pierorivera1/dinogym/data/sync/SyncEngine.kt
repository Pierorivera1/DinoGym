package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.apis.SyncApi
import io.github.pierorivera1.dinogym.api.infrastructure.HttpResponse
import io.github.pierorivera1.dinogym.api.models.PushResult
import io.github.pierorivera1.dinogym.data.account.AccessTokenResult
import io.github.pierorivera1.dinogym.data.account.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** How one sync run ended. The worker turns it into WorkManager's result. */
sealed interface SyncOutcome {
    /** Pushed and pulled. [moreWaiting]: rows changed while it ran, so run again soon. */
    data class Synced(val moreWaiting: Boolean) : SyncOutcome

    /** The server could not be reached. Nothing is lost; try again later. */
    data object Offline : SyncOutcome

    /** The server failed (5xx) or throttled (429). A retry is safe: rows already written come back `stale`. */
    data object RetryLater : SyncOutcome

    /** Nobody is signed in, or the session ended during the run. Stop until the next sign-in. */
    data object SignedOut : SyncOutcome

    /** A refusal a retry will not fix (400, a second 401, a row too big). Reported, not retried now. */
    data class Failed(val code: String) : SyncOutcome
}

/**
 * Pushes this phone's pending rows, then pulls everything newer than the cursor
 * (decisions 51, 62, 86-89). One run at a time ([mutex]); WorkManager decides when.
 *
 * Push: the pending snapshot is split by [PushBatcher]. Per row, `accepted` and `stale` clear
 * the pending entry, `rejected` (a clock far ahead) keeps it for a later run without looping.
 * A 413 splits the batch in two and tries again.
 *
 * Pull: pages of [PULL_LIMIT] until `hasMore` is false, each applied with its cursor in one
 * transaction. Rows already applied (the cursor waits 30 s, decision 86) apply as ties.
 *
 * Auth: a 401 asks [SessionManager] for a fresh token once and repeats the call once.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: SyncApi,
    private val sessions: SessionManager,
    private val local: SyncLocalStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)

    /** True while a run is pushing or pulling, for the "Syncing…" row. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        _running.value = true
        try {
            val outcome = try {
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // A body that does not match the contract, or a database error: a bug, reported.
                SyncOutcome.Failed("unexpected")
            }
            when (outcome) {
                is SyncOutcome.Synced -> local.recordSuccess(clock.millis())
                is SyncOutcome.Failed -> local.recordFailure(outcome.code)
                SyncOutcome.RetryLater -> local.recordFailure("server")
                SyncOutcome.Offline, SyncOutcome.SignedOut -> Unit
            }
            outcome
        } finally {
            _running.value = false
        }
    }

    private suspend fun run(): SyncOutcome {
        val auth = Auth()
        val pending = local.pendingRows()
        val snapshot = pending.maxOfOrNull { it.seq } ?: 0
        push(pending, auth)?.let { return it }
        pull(auth)?.let { return it }
        return SyncOutcome.Synced(moreWaiting = local.latestPending() > snapshot)
    }

    /** Null when every batch got an answer; otherwise why the run stops. */
    private suspend fun push(pending: List<PendingRow>, auth: Auth): SyncOutcome? {
        val queue = ArrayDeque(PushBatcher.split(pending) { sizeOf(it) })
        while (queue.isNotEmpty()) {
            val batch = queue.removeFirst()
            val response = when (val call = auth.call { api.pushChanges(batch.map { it.row }.toChangeSet()) }) {
                is Call.Done -> call.response
                is Call.Stop -> return call.outcome
            }
            when {
                response.success -> {
                    val results = response.body().results.associateBy { RowRef(it.table, it.id) }
                    local.clearPushed(
                        batch.filter {
                            val status = results[it.row.ref]?.status
                            status == PushResult.Status.ACCEPTED || status == PushResult.Status.STALE
                        },
                    )
                }
                response.status == PAYLOAD_TOO_LARGE && batch.size > 1 -> {
                    queue.addFirst(batch.subList(batch.size / 2, batch.size))
                    queue.addFirst(batch.subList(0, batch.size / 2))
                }
                else -> return failure(response.status, "push")
            }
        }
        return null
    }

    private suspend fun pull(auth: Auth): SyncOutcome? {
        var since = local.cursor()
        while (true) {
            val response = when (val call = auth.call { api.pullChanges(since, PULL_LIMIT) }) {
                is Call.Done -> call.response
                is Call.Stop -> return call.outcome
            }
            if (!response.success) return failure(response.status, "pull")
            val page = response.body()
            local.applyPage(page.changes.toRows(), page.cursor)
            // The second check guards against a server that says "more" without moving.
            if (!page.hasMore || page.cursor <= since) return null
            since = page.cursor
        }
    }

    private fun failure(status: Int, step: String): SyncOutcome = when {
        status == TOO_MANY_REQUESTS || status >= SERVER_ERROR -> SyncOutcome.RetryLater
        else -> SyncOutcome.Failed("${step}_$status")
    }

    private fun sizeOf(row: PendingRow): Int =
        DinoGymApi.JSON.encodeToString(listOf(row.row).toChangeSet()).encodeToByteArray().size

    private sealed interface Call<T> {
        data class Done<T>(val response: HttpResponse<T>) : Call<T>
        data class Stop<T>(val outcome: SyncOutcome) : Call<T>
    }

    /** The token for this run, refreshed at most once after a 401. */
    private inner class Auth {
        private var token: String? = null
        private var refreshed = false

        suspend fun <T> call(request: suspend () -> HttpResponse<T>): Call<T> {
            val first = token ?: when (val result = sessions.accessToken()) {
                is AccessTokenResult.Token -> result.value
                AccessTokenResult.Offline -> return Call.Stop(SyncOutcome.Offline)
                AccessTokenResult.SignedOut -> return Call.Stop(SyncOutcome.SignedOut)
            }
            token = first
            val response = send(first, request) ?: return Call.Stop(SyncOutcome.Offline)
            if (response.status != UNAUTHORIZED) return Call.Done(response)
            if (refreshed) return Call.Stop(SyncOutcome.Failed("unauthorized"))
            refreshed = true
            val second = when (val result = sessions.accessToken(rejected = first)) {
                is AccessTokenResult.Token -> result.value
                AccessTokenResult.Offline -> return Call.Stop(SyncOutcome.Offline)
                AccessTokenResult.SignedOut -> return Call.Stop(SyncOutcome.SignedOut)
            }
            token = second
            val retried = send(second, request) ?: return Call.Stop(SyncOutcome.Offline)
            return if (retried.status == UNAUTHORIZED) Call.Stop(SyncOutcome.Failed("unauthorized")) else Call.Done(retried)
        }

        /** Null when the request failed on the network; a sync call is safe to repeat later. */
        private suspend fun <T> send(token: String, request: suspend () -> HttpResponse<T>): HttpResponse<T>? = try {
            api.setBearerToken(token)
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        /** Rows per pull page; the server allows up to 1000 (decision 62). */
        const val PULL_LIMIT = 1000
        private const val UNAUTHORIZED = 401
        private const val PAYLOAD_TOO_LARGE = 413
        private const val TOO_MANY_REQUESTS = 429
        private const val SERVER_ERROR = 500
    }
}
