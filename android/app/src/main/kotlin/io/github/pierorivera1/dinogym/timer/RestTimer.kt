package io.github.pierorivera1.dinogym.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.pierorivera1.dinogym.di.ApplicationScope
import io.github.pierorivera1.dinogym.di.RestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rest timer. A rest is only an end time, so there is no foreground service and nothing
 * ticks in the background:
 * - the system's notification chronometer shows the countdown ([RestNotifier]);
 * - an exact alarm wakes the app at the end ([RestAlarm] → [RestOverReceiver]);
 * - the state is saved in DataStore, so a rest survives the app being killed.
 *
 * The rest screen observes [state] and works out the remaining time with [RestCountdown].
 * Call [start], [addSeconds] and [cancel] from the main thread (click handlers).
 */
@Singleton
class RestTimer @Inject constructor(
    @RestTimerStore private val store: DataStore<Preferences>,
    private val alarm: RestAlarm,
    private val notifier: RestNotifier,
    private val appVisibility: AppVisibility,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RestTimerState?>(null)

    /** The rest in progress, or null when there is none. */
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    /** Serialises DataStore writes so the last one always holds the latest state. */
    private val writeLock = Mutex()

    /** Set by any change, so loading the saved rest never overwrites a newer one. */
    @Volatile
    private var changedSinceLaunch = false

    /** Starts a rest of [seconds] (clamped to 0:00–9:00). 0:00 means no rest, like [cancel]. */
    fun start(seconds: Int, nextLabel: String) {
        val rest = RestCountdown.start(seconds, nextLabel, now())
        if (rest == null) {
            cancel()
            return
        }
        notifier.cancelRestOver()
        apply(rest)
    }

    /** −15s / +15s. Remaining time stays within 0:00–9:00; at 0:00 the rest ends now. */
    fun addSeconds(deltaSeconds: Int) {
        val current = _state.value ?: return
        apply(RestCountdown.addSeconds(current, deltaSeconds, now()))
    }

    /** Skips the rest: clears it with no "rest over" notification. */
    fun cancel() {
        changedSinceLaunch = true
        _state.value = null
        alarm.cancel()
        notifier.cancelRunning()
        persistInBackground()
    }

    /** See [RestNotifier.areNotificationsAllowed]. */
    fun areNotificationsAllowed(): Boolean = notifier.areNotificationsAllowed()

    /** Loads the saved rest at app start. Called from `DinoGymApp.onCreate`. */
    fun restoreInBackground() {
        scope.launch { restore() }
    }

    /**
     * Loads the saved rest. A rest that ended while the app was dead is cleared (its alarm
     * already fired, or was lost to a reboot). A rest still running gets its alarm set again,
     * which is harmless if it was already set and fixes the case where it was lost.
     */
    internal suspend fun restore() {
        val saved = store.data.first().toRestState() ?: return
        if (changedSinceLaunch) return
        if (RestCountdown.isExpired(saved, now())) {
            notifier.cancelRunning()
            persist() // writes the current state, which is still null: clears the saved rest
        } else {
            _state.value = saved
            alarm.schedule(saved)
        }
    }

    /**
     * Called by [RestOverReceiver] when the alarm fires. Clears the rest and, unless the app
     * is on screen (the rest screen shows the end itself), posts "rest over" with sound.
     */
    internal suspend fun onRestOver(nextLabel: String) {
        val current = _state.value
        // A rest started or extended after this alarm was set is still running: not over.
        if (current != null && !RestCountdown.isExpired(current, now())) return
        changedSinceLaunch = true
        _state.compareAndSet(current, null)
        if (appVisibility.isVisible) notifier.cancelRunning() else notifier.showRestOver(nextLabel)
        persist()
    }

    private fun apply(rest: RestTimerState) {
        changedSinceLaunch = true
        _state.value = rest
        alarm.schedule(rest) // an end time in the past fires at once
        if (!RestCountdown.isExpired(rest, now())) notifier.showRunning(rest, now())
        persistInBackground()
    }

    private fun persistInBackground() {
        scope.launch { persist() }
    }

    /** Saves whatever the state is now, so writes that run late still save the latest. */
    private suspend fun persist() = writeLock.withLock {
        val rest = _state.value
        store.edit { prefs ->
            if (rest == null) {
                prefs.clear()
            } else {
                prefs[KEY_ENDS_AT] = rest.endsAtMillis
                prefs[KEY_TOTAL] = rest.totalSeconds
                prefs[KEY_NEXT] = rest.nextLabel
            }
        }
    }

    private fun now(): Long = clock.millis()

    private fun Preferences.toRestState(): RestTimerState? {
        val endsAt = this[KEY_ENDS_AT] ?: return null
        return RestTimerState(endsAt, this[KEY_TOTAL] ?: 0, this[KEY_NEXT].orEmpty())
    }

    internal companion object {
        val KEY_ENDS_AT = longPreferencesKey("ends_at_millis")
        val KEY_TOTAL = intPreferencesKey("total_seconds")
        val KEY_NEXT = stringPreferencesKey("next_label")
    }
}
