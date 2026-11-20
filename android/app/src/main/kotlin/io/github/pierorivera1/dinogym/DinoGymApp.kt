package io.github.pierorivera1.dinogym

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.pierorivera1.dinogym.data.seed.FirstRunSeeder
import io.github.pierorivera1.dinogym.data.sync.SyncController
import io.github.pierorivera1.dinogym.data.sync.SyncWorkerFactory
import io.github.pierorivera1.dinogym.di.ApplicationScope
import io.github.pierorivera1.dinogym.reminders.FcmDeviceRegistry
import io.github.pierorivera1.dinogym.reminders.FirebaseSetup
import io.github.pierorivera1.dinogym.reminders.ReminderNotifier
import io.github.pierorivera1.dinogym.reminders.ReminderRepository
import io.github.pierorivera1.dinogym.timer.AppVisibility
import io.github.pierorivera1.dinogym.timer.RestNotifier
import io.github.pierorivera1.dinogym.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class. Hilt builds its dependency graph from here. It also configures
 * WorkManager (initialised on first use, not at startup; see the manifest) so the sync
 * worker gets its engine from Hilt.
 */
@HiltAndroidApp
class DinoGymApp : Application(), Configuration.Provider {

    @Inject
    lateinit var firstRunSeeder: FirstRunSeeder

    @Inject
    lateinit var appVisibility: AppVisibility

    @Inject
    lateinit var restNotifier: RestNotifier

    @Inject
    lateinit var restTimer: RestTimer

    @Inject
    lateinit var syncController: SyncController

    @Inject
    lateinit var syncWorkerFactory: SyncWorkerFactory

    @Inject
    lateinit var firebase: FirebaseSetup

    @Inject
    lateinit var reminderNotifier: ReminderNotifier

    @Inject
    lateinit var reminders: ReminderRepository

    @Inject
    lateinit var deviceRegistry: FcmDeviceRegistry

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(syncWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Runs on the IO dispatcher; screens observe Flows, so they update when the seed lands.
        firstRunSeeder.seedInBackground()

        // Rest timer: track whether the app is on screen, make sure the notification channels
        // exist (also when the process starts only to deliver the end-of-rest alarm), and load
        // a rest saved before the app was killed, clearing it if it has already ended.
        registerActivityLifecycleCallbacks(appVisibility)
        restNotifier.createChannels()
        restTimer.restoreInBackground()

        // Sync (task 21): follows the signed-in account in the background. Nothing waits on it.
        syncController.start()

        // Reminders (task 24): Firebase from BuildConfig, the two channels, registering this
        // phone whenever an account signs in, and moving the reminders to a new time zone.
        firebase.start()
        reminderNotifier.createChannels()
        deviceRegistry.start()
        appScope.launch { reminders.followZone() }
    }
}
