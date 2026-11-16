package io.github.pierorivera1.dinogym

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The app starts (Hilt graph, database, seed) and the activity reaches RESUMED. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun launchesToResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
