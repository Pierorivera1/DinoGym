package io.github.pierorivera1.dinogym.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** When sync runs (decision 91). An interface so the controller is tested without WorkManager. */
interface SyncScheduler {
    /** Every few hours while signed in, when there is a network. */
    fun schedulePeriodic()

    /** As soon as there is a network: right after sign-in, and SYNC NOW. */
    fun syncNow()

    /** A short while after data changes, so a burst of logged sets goes up in one run. */
    fun syncSoon()

    /** Sign-out: nothing runs until the next sign-in. */
    fun cancelAll()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun syncNow() = enqueueOnce(NOW, delaySeconds = 0)

    override fun syncSoon() = enqueueOnce(SOON, delaySeconds = SOON_DELAY_SECONDS)

    /**
     * KEEP: while a run is waiting, more changes join it instead of restarting its delay, so
     * a long workout still syncs. Changes made while a run is already going are caught by
     * that run asking to go again ([SyncOutcome.Synced.moreWaiting]).
     */
    private fun enqueueOnce(name: String, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        listOf(PERIODIC, NOW, SOON).forEach { workManager.cancelUniqueWork(it) }
    }

    private companion object {
        const val PERIODIC = "sync-periodic"
        const val NOW = "sync-now"
        const val SOON = "sync-soon"
        const val PERIODIC_HOURS = 6L
        const val SOON_DELAY_SECONDS = 60L
        const val BACKOFF_SECONDS = 60L
    }
}

/**
 * Starts and stops sync with the account (decisions 90-91). Runs for the life of the app:
 * - Signed in: the account is checked against the phone's last one (a new account gets the
 *   whole history uploaded and starts pulling from 0), the periodic run is scheduled, a new
 *   account syncs at once, and every synced write schedules a run soon after.
 * - Signed out (by the user, or a session that ended): every scheduled run is cancelled.
 *   Pending changes stay marked for the next sign-in.
 */
@Singleton
class SyncController @Inject constructor(
    private val sessions: SessionManager,
    private val local: SyncLocalStore,
    private val scheduler: SyncScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { follow() }
    }

    internal suspend fun follow() {
        sessions.userId.collectLatest { userId ->
            if (userId == null) {
                scheduler.cancelAll()
                return@collectLatest
            }
            val newAccount = local.startAccount(userId)
            scheduler.schedulePeriodic()
            if (newAccount) scheduler.syncNow()
            local.observeLatestPending()
                .filter { it != null }
                .distinctUntilChanged()
                .collect { scheduler.syncSoon() }
        }
    }
}

/** One sync run, from any of the three unique works. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val outcome = engine.sync()) {
        is SyncOutcome.Synced -> if (outcome.moreWaiting) Result.retry() else Result.success()
        SyncOutcome.Offline, SyncOutcome.RetryLater -> Result.retry()
        SyncOutcome.SignedOut -> Result.success()
        is SyncOutcome.Failed -> Result.failure()
    }
}

/** Builds [SyncWorker] with its engine; the app hands this to WorkManager's configuration. */
class SyncWorkerFactory @Inject constructor(
    private val engine: Provider<SyncEngine>,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, engine.get()) else null
}
