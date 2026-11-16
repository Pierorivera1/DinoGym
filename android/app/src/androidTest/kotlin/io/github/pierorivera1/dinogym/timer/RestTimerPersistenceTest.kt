package io.github.pierorivera1.dinogym.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.util.UUID

/** The rest is saved to DataStore and read back by a fresh [RestTimer], as after a process kill. */
@RunWith(AndroidJUnit4::class)
class RestTimerPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.cacheDir, "rest_timer_test_${UUID.randomUUID()}.preferences_pb")
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun newTimer() = RestTimer(
        store = store,
        alarm = RestAlarm(context),
        notifier = RestNotifier(context),
        appVisibility = AppVisibility(),
        clock = Clock.systemUTC(),
        scope = scope,
    )

    @After
    fun tearDown() {
        RestAlarm(context).cancel()
        RestNotifier(context).cancelRunning()
        scope.cancel()
        file.delete()
    }

    @Test
    fun aNewInstanceReadsTheSavedRest() = runBlocking {
        val first = newTimer()
        // start() touches the alarm and notification APIs, like a click handler on the main thread.
        withContext(Dispatchers.Main) { first.start(90, "Dips, set 2 of 3") }
        val started = first.state.value!!
        withTimeout(5_000) { store.data.first { it[RestTimer.KEY_ENDS_AT] != null } }

        val second = newTimer()
        second.restore()

        assertEquals(started, second.state.value)
    }

    @Test
    fun anExpiredRestIsClearedOnRestore() = runBlocking {
        store.edit {
            it[RestTimer.KEY_ENDS_AT] = System.currentTimeMillis() - 1_000
            it[RestTimer.KEY_TOTAL] = 90
            it[RestTimer.KEY_NEXT] = "Dips"
        }

        val timer = newTimer()
        timer.restore()

        assertNull(timer.state.value)
        assertNull(store.data.first()[RestTimer.KEY_ENDS_AT])
    }
}
