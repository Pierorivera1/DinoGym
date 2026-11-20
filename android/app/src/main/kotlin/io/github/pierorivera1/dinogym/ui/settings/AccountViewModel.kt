package io.github.pierorivera1.dinogym.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.account.GoogleCredentialResult
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.SignInResult
import io.github.pierorivera1.dinogym.data.sync.SyncSnapshot
import io.github.pierorivera1.dinogym.data.sync.SyncStatus
import io.github.pierorivera1.dinogym.reminders.DeviceRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Why a sign-in did not finish. A cancelled sheet is not one: it shows nothing. */
enum class AccountError { NO_GOOGLE_ACCOUNT, OFFLINE, FAILED }

/** The sync status row of the signed-in card (mock `Account.dc.html`, task 21). */
sealed interface SyncRowState {
    data object Syncing : SyncRowState

    /** The check: "Synced · 2 min ago". */
    data class Synced(val minutesAgo: Long) : SyncRowState

    /** The hourglass: "Offline · 3 changes waiting". */
    data class Offline(val pending: Int) : SyncRowState

    /** Online with changes not sent yet (a run is scheduled): "Waiting to sync · 3 changes waiting". */
    data class Waiting(val pending: Int) : SyncRowState

    /** The last run failed; it tries again on its own. */
    data class Failed(val pending: Int) : SyncRowState
}

/** The ACCOUNT section of Settings (mock `Account.dc.html`). */
sealed interface AccountUiState {
    /** Before the stored session is read, so the card does not flash the wrong state. */
    data object Loading : AccountUiState

    data class SignedOut(val signingIn: Boolean = false, val error: AccountError? = null) : AccountUiState

    data class SignedIn(
        val displayName: String,
        val email: String,
        val confirmingSignOut: Boolean = false,
        val sync: SyncRowState = SyncRowState.Synced(0),
        /** SYNC NOW: off while offline or while a run is going. */
        val canSyncNow: Boolean = true,
        /** Changes not synced yet, for the sign-out dialog. */
        val pendingChanges: Int = 0,
    ) : AccountUiState
}

/**
 * Settings' ACCOUNT section: sign in with Google, show who is signed in and how sync is doing,
 * SYNC NOW, and sign out after a confirmation. The Google sheet needs an Activity, so [signIn]
 * takes the request as a function the screen builds; the ViewModel never holds the Activity.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessions: SessionManager,
    private val sync: SyncStatus,
    private val clock: Clock,
    private val devices: DeviceRegistry,
) : ViewModel() {

    private val signingIn = MutableStateFlow(false)
    private val error = MutableStateFlow<AccountError?>(null)
    private val confirmingSignOut = MutableStateFlow(false)

    /** The time, every [TICK_MILLIS], so "2 min ago" moves on while the screen is open. */
    private val now: Flow<Long> = flow {
        while (true) {
            emit(clock.millis())
            delay(TICK_MILLIS)
        }
    }

    private val syncRow = combine(sync.snapshot, now) { snapshot, time -> snapshot to time }

    val state: StateFlow<AccountUiState> =
        combine(sessions.account, signingIn, error, confirmingSignOut, syncRow) { account, busy, err, confirming, (snapshot, time) ->
            if (account == null) {
                AccountUiState.SignedOut(signingIn = busy, error = err)
            } else {
                AccountUiState.SignedIn(
                    displayName = account.displayName,
                    email = account.email,
                    confirmingSignOut = confirming,
                    sync = syncRowOf(snapshot, time),
                    canSyncNow = snapshot.online && !snapshot.running,
                    pendingChanges = snapshot.local.pending,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState.Loading)

    /** Shows Google's sheet through [requestCredential], then exchanges the token with the API. */
    fun signIn(requestCredential: suspend () -> GoogleCredentialResult) {
        if (signingIn.value) return
        signingIn.value = true
        error.value = null
        viewModelScope.launch {
            try {
                error.value = when (val credential = requestCredential()) {
                    GoogleCredentialResult.Cancelled -> null
                    GoogleCredentialResult.NoAccount -> AccountError.NO_GOOGLE_ACCOUNT
                    GoogleCredentialResult.Failed -> AccountError.FAILED
                    is GoogleCredentialResult.Success ->
                        when (sessions.signIn(credential.idToken, credential.displayName, credential.email)) {
                            SignInResult.Success -> null
                            SignInResult.Offline -> AccountError.OFFLINE
                            SignInResult.Failed -> AccountError.FAILED
                        }
                }
            } finally {
                signingIn.value = false
            }
        }
    }

    fun syncNow() {
        sync.syncNow()
    }

    fun askSignOut() {
        confirmingSignOut.value = true
    }

    fun cancelSignOut() {
        confirmingSignOut.value = false
    }

    fun confirmSignOut() {
        confirmingSignOut.value = false
        viewModelScope.launch {
            // While the session is still there to authorize it: stop reminders to this phone.
            devices.unregister()
            sessions.signOut()
        }
    }

    companion object {
        const val TICK_MILLIS = 30_000L

        /** Which sync row to show. A run in progress first, then no connection, then an error. */
        fun syncRowOf(snapshot: SyncSnapshot, now: Long): SyncRowState {
            val pending = snapshot.local.pending
            val last = snapshot.local.lastSyncedAt
            return when {
                snapshot.running -> SyncRowState.Syncing
                !snapshot.online -> SyncRowState.Offline(pending)
                snapshot.local.failed -> SyncRowState.Failed(pending)
                pending > 0 || last == null -> SyncRowState.Waiting(pending)
                else -> SyncRowState.Synced(((now - last) / 60_000).coerceAtLeast(0))
            }
        }
    }
}
