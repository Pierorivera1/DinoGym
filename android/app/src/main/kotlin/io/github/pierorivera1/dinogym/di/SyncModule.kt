package io.github.pierorivera1.dinogym.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.apis.SharesApi
import io.github.pierorivera1.dinogym.api.apis.SyncApi
import io.github.pierorivera1.dinogym.data.sync.ConnectivityNetworkMonitor
import io.github.pierorivera1.dinogym.data.sync.DefaultSyncStatus
import io.github.pierorivera1.dinogym.data.sync.NetworkMonitor
import io.github.pierorivera1.dinogym.data.sync.RoomSyncLocalStore
import io.github.pierorivera1.dinogym.data.sync.SyncLocalStore
import io.github.pierorivera1.dinogym.data.sync.SyncScheduler
import io.github.pierorivera1.dinogym.data.sync.SyncStatus
import io.github.pierorivera1.dinogym.data.sync.WorkManagerSyncScheduler

/** Sync (task 21): the engine and controller are injected by their constructors. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun bindLocalStore(store: RoomSyncLocalStore): SyncLocalStore

    @Binds
    abstract fun bindScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindNetworkMonitor(monitor: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun bindStatus(status: DefaultSyncStatus): SyncStatus

    companion object {
        @Provides
        fun provideSyncApi(api: DinoGymApi): SyncApi = api.sync

        /** Shareable routines (task 23). Publishing sets its own bearer token per call. */
        @Provides
        fun provideSharesApi(api: DinoGymApi): SharesApi = api.shares
    }
}
