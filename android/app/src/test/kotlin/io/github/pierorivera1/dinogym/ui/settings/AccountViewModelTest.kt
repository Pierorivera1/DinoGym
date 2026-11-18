package io.github.pierorivera1.dinogym.ui.settings

import io.github.pierorivera1.dinogym.data.account.FakeServer
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.session
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.unauthorized
import io.github.pierorivera1.dinogym.data.account.FakeSessionStore
import io.github.pierorivera1.dinogym.data.account.GoogleCredentialResult
import io.github.pierorivera1.dinogym.data.account.MutableClock
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.storedSession
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val store = FakeSessionStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        server: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { error("no request expected") },
    ): AccountViewModel {
        val vm = AccountViewModel(SessionManager(store, FakeServer(server).api.auth, MutableClock(0)))
        // stateIn(WhileSubscribed) needs a collector.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private val google = GoogleCredentialResult.Success("id-token", "Piero", "piero@example.com")

    @Test
    fun signedOut_signingIn_signedIn() = runTest {
        val vm = viewModel { session("a1", 900_000, "r1") }
        assertEquals(AccountUiState.SignedOut(), vm.state.value)

        val sheet = CompletableDeferred<GoogleCredentialResult>()
        vm.signIn { sheet.await() }
        assertEquals(AccountUiState.SignedOut(signingIn = true), vm.state.value)

        sheet.complete(google)
        val signedIn = vm.state.first { it is AccountUiState.SignedIn }

        assertEquals(AccountUiState.SignedIn("Piero", "piero@example.com"), signedIn)
        assertEquals("r1", store.state.value!!.refreshToken)
    }

    @Test
    fun aSecondTapWhileBusy_isIgnored() = runTest {
        val vm = viewModel()
        val sheet = CompletableDeferred<GoogleCredentialResult>()
        var sheets = 0
        vm.signIn { sheets++; sheet.await() }
        vm.signIn { sheets++; sheet.await() }

        assertEquals(1, sheets)
        sheet.complete(GoogleCredentialResult.Cancelled)
    }

    @Test
    fun cancelledSheet_goesBackToSignedOut_withNoMessage() = runTest {
        val vm = viewModel()

        vm.signIn { GoogleCredentialResult.Cancelled }

        assertEquals(AccountUiState.SignedOut(), vm.state.first { it == AccountUiState.SignedOut() })
        assertNull(store.state.value)
    }

    @Test
    fun noGoogleAccount_showsItsMessage() = runTest {
        val vm = viewModel()

        vm.signIn { GoogleCredentialResult.NoAccount }

        assertEquals(
            AccountUiState.SignedOut(error = AccountError.NO_GOOGLE_ACCOUNT),
            vm.state.first { it is AccountUiState.SignedOut && !it.signingIn },
        )
    }

    @Test
    fun offlineAndServerErrors_showTheirMessages() = runTest {
        val offline = viewModel { throw UnknownHostException("no network") }
        offline.signIn { google }
        assertEquals(
            AccountUiState.SignedOut(error = AccountError.OFFLINE),
            offline.state.first { it is AccountUiState.SignedOut && it.error != null },
        )

        val refused = viewModel { unauthorized() }
        refused.signIn { google }
        assertEquals(
            AccountUiState.SignedOut(error = AccountError.FAILED),
            refused.state.first { it is AccountUiState.SignedOut && it.error != null },
        )
    }

    @Test
    fun aNewAttempt_clearsTheOldMessage() = runTest {
        val vm = viewModel()
        vm.signIn { GoogleCredentialResult.Failed }
        vm.state.first { it is AccountUiState.SignedOut && it.error != null }

        val sheet = CompletableDeferred<GoogleCredentialResult>()
        vm.signIn { sheet.await() }

        assertEquals(AccountUiState.SignedOut(signingIn = true), vm.state.value)
        sheet.complete(GoogleCredentialResult.Cancelled)
    }

    @Test
    fun signOut_asksFirst_thenClearsOnlyTheSession() = runTest {
        store.state.value = storedSession()
        val vm = viewModel()
        assertEquals(AccountUiState.SignedIn("Piero", "piero@example.com"), vm.state.first { it is AccountUiState.SignedIn })

        vm.askSignOut()
        assertEquals(AccountUiState.SignedIn("Piero", "piero@example.com", confirmingSignOut = true), vm.state.value)

        vm.cancelSignOut()
        assertEquals(AccountUiState.SignedIn("Piero", "piero@example.com"), vm.state.value)

        vm.askSignOut()
        vm.confirmSignOut()

        assertTrue(vm.state.first { it is AccountUiState.SignedOut } is AccountUiState.SignedOut)
        assertNull(store.state.value)
    }
}
