package io.github.pierorivera1.dinogym.ui.settings

import io.github.pierorivera1.dinogym.data.account.FakeServer
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.session
import io.github.pierorivera1.dinogym.data.account.FakeServer.Companion.unauthorized
import io.github.pierorivera1.dinogym.data.account.FakeSessionStore
import io.github.pierorivera1.dinogym.data.account.GoogleCredentialResult
import io.github.pierorivera1.dinogym.data.account.MutableClock
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.account.storedSession
import io.github.pierorivera1.dinogym.data.sync.LocalSyncState
import io.github.pierorivera1.dinogym.data.sync.SyncSnapshot
import io.github.pierorivera1.dinogym.data.sync.SyncStatus
import io.github.pierorivera1.dinogym.reminders.DeviceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val clock = MutableClock(10 * 60_000)

    /** Sync's inputs, set by each test. By default: online, idle, synced just now. */
    private val sync = object : SyncStatus {
        val state = MutableStateFlow(SyncSnapshot(LocalSyncState(lastSyncedAt = clock.millis)))
        var syncNowCalls = 0
        override val snapshot: Flow<SyncSnapshot> = state
        override fun syncNow() {
            syncNowCalls++
        }
    }

    /** Records the order of sign-out's steps: the device must go while the session is there. */
    private val devices = object : DeviceRegistry {
        val unregisteredWithSession = mutableListOf<Boolean>()
        override suspend fun register(newToken: String?) = Unit
        override suspend fun unregister() {
            unregisteredWithSession += store.state.value != null
        }
    }

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
        val vm = AccountViewModel(SessionManager(store, FakeServer(server).api.auth, clock), sync, clock, devices)
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
        assertEquals(listOf(true), devices.unregisteredWithSession)
    }

    // The sync row (task 21)

    private suspend fun TestScope.signedIn(): AccountUiState.SignedIn {
        store.state.value = storedSession()
        val vm = viewModel()
        return vm.state.first { it is AccountUiState.SignedIn } as AccountUiState.SignedIn
    }

    @Test
    fun syncedTwoMinutesAgo() = runTest {
        sync.state.value = SyncSnapshot(LocalSyncState(lastSyncedAt = clock.millis - 2 * 60_000 - 5_000))

        val state = signedIn()

        assertEquals(SyncRowState.Synced(minutesAgo = 2), state.sync)
        assertTrue(state.canSyncNow)
    }

    @Test
    fun offlineWithChangesWaiting_disablesSyncNow_andTellsTheSignOutDialog() = runTest {
        sync.state.value = SyncSnapshot(LocalSyncState(pending = 3, lastSyncedAt = 0), online = false)

        val state = signedIn()

        assertEquals(SyncRowState.Offline(pending = 3), state.sync)
        assertEquals(false, state.canSyncNow)
        assertEquals(3, state.pendingChanges)
    }

    @Test
    fun aRunInProgress_showsSyncing_andDisablesSyncNow() = runTest {
        sync.state.value = SyncSnapshot(LocalSyncState(pending = 3), running = true)

        val state = signedIn()

        assertEquals(SyncRowState.Syncing, state.sync)
        assertEquals(false, state.canSyncNow)
    }

    @Test
    fun aFailedRun_showsTheError_untilASuccess() = runTest {
        sync.state.value = SyncSnapshot(LocalSyncState(pending = 1, lastSyncedAt = 0, failed = true))

        assertEquals(SyncRowState.Failed(pending = 1), signedIn().sync)
    }

    @Test
    fun onlineWithChangesNotSentYet_isWaiting() = runTest {
        sync.state.value = SyncSnapshot(LocalSyncState(pending = 2, lastSyncedAt = 0))

        assertEquals(SyncRowState.Waiting(pending = 2), signedIn().sync)
    }

    @Test
    fun syncNow_asksTheScheduler() = runTest {
        store.state.value = storedSession()
        val vm = viewModel()

        vm.syncNow()

        assertEquals(1, sync.syncNowCalls)
    }
}
