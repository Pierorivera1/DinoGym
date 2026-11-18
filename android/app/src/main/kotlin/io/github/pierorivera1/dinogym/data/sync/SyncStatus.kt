package io.github.pierorivera1.dinogym.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the phone has a working connection, for the "Offline" row and SYNC NOW. */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork).hasInternet()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities?.hasInternet(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** Everything the ACCOUNT card's sync row shows, before it is turned into words. */
data class SyncSnapshot(
    val local: LocalSyncState = LocalSyncState(),
    val running: Boolean = false,
    val online: Boolean = true,
)

/** The sync row's inputs in one flow, and SYNC NOW. */
interface SyncStatus {
    val snapshot: Flow<SyncSnapshot>

    fun syncNow()
}

@Singleton
class DefaultSyncStatus @Inject constructor(
    local: SyncLocalStore,
    engine: SyncEngine,
    network: NetworkMonitor,
    private val scheduler: SyncScheduler,
) : SyncStatus {
    override val snapshot: Flow<SyncSnapshot> =
        combine(local.state, engine.running, network.isOnline, ::SyncSnapshot)

    override fun syncNow() = scheduler.syncNow()
}
