package io.github.pierorivera1.dinogym.timer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knows whether any of the app's activities is on screen, by counting started activities.
 * Registered once in `DinoGymApp`.
 *
 * The rest timer uses it to skip the "rest over" notification while the app is visible,
 * because the rest screen shows the end itself. In a process started just to deliver the
 * alarm, nothing has started, so the count is 0 and the notification is posted.
 *
 * Rejected: `ProcessLifecycleOwner`, which does the same but needs the lifecycle-process
 * library; and `ActivityManager.getMyMemoryState`, whose answer while a broadcast receiver
 * runs is harder to explain.
 */
@Singleton
class AppVisibility @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private val startedActivities = AtomicInteger(0)

    /** True while at least one activity is started (visible). */
    val isVisible: Boolean get() = startedActivities.get() > 0

    override fun onActivityStarted(activity: Activity) {
        startedActivities.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities.decrementAndGet()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
