package io.github.pierorivera1.dinogym.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.account.GoogleCredentialResult
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a sign-in did not finish. A cancelled sheet is not one: it shows nothing. */
enum class AccountError { NO_GOOGLE_ACCOUNT, OFFLINE, FAILED }

/** The ACCOUNT section of Settings (mock `Account.dc.html`). */
sealed interface AccountUiState {
    /** Before the stored session is read, so the card does not flash the wrong state. */
    data object Loading : AccountUiState

    data class SignedOut(val signingIn: Boolean = false, val error: AccountError? = null) : AccountUiState

    data class SignedIn(
        val displayName: String,
        val email: String,
        val confirmingSignOut: Boolean = false,
    ) : AccountUiState
}

/**
 * Settings' ACCOUNT section: sign in with Google, show who is signed in, sign out after a
 * confirmation. The Google sheet needs an Activity, so [signIn] takes the request as a
 * function the screen builds; the ViewModel never holds the Activity.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessions: SessionManager,
) : ViewModel() {

    private val signingIn = MutableStateFlow(false)
    private val error = MutableStateFlow<AccountError?>(null)
    private val confirmingSignOut = MutableStateFlow(false)

    val state: StateFlow<AccountUiState> =
        combine(sessions.account, signingIn, error, confirmingSignOut) { account, busy, err, confirming ->
            if (account == null) {
                AccountUiState.SignedOut(signingIn = busy, error = err)
            } else {
                AccountUiState.SignedIn(account.displayName, account.email, confirming)
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

    fun askSignOut() {
        confirmingSignOut.value = true
    }

    fun cancelSignOut() {
        confirmingSignOut.value = false
    }

    fun confirmSignOut() {
        confirmingSignOut.value = false
        viewModelScope.launch { sessions.signOut() }
    }
}
