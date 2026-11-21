package io.github.pierorivera1.dinogym.crash

import android.content.Context
import com.google.firebase.crashlytics.internal.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Crashlytics' SDK throws "The Crashlytics build ID is missing" at start unless the app has a
 * mapping file id resource, which build.gradle.kts writes in place of the Crashlytics Gradle
 * plugin (decision 120). This reads it through the SDK's own lookup, so a renamed resource or a
 * build that drops resValues fails here instead of on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class CrashlyticsBuildIdTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `the SDK finds the mapping file id`() {
        assertEquals("0".repeat(32), CommonUtils.getMappingFileId(context))
    }
}
