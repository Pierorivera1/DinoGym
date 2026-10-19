package com.dinogym

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.dinogym.timer.RestTimerService
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RestTimerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @After fun stopTimer() { RestTimerService.stop(context) }

    @Suppress("DEPRECATION") // getRunningServices still exposes the caller's own services.
    @Test fun completionKeepsServiceAliveForTwoSecondAlarm() {
        compose.setContent { Text("Timer alarm test") }
        val activityManager = context.getSystemService(ActivityManager::class.java)
        fun timerRunning() = activityManager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == RestTimerService::class.java.name
        }
        compose.runOnIdle { RestTimerService.start(context, 1) }
        compose.waitUntil(5000) { RestTimerService.state.value.active }
        compose.waitUntil(5000) { !RestTimerService.state.value.active }
        val started = SystemClock.elapsedRealtime()
        // Native ToneGenerator playback is not observable through activePlaybackConfigurations.
        assertTrue(timerRunning())
        SystemClock.sleep(1500)
        assertTrue("Service should retain the alarm after 1.5 seconds", timerRunning())
        compose.waitUntil(1500) { !timerRunning() }
        assertTrue("Service should stop after about two seconds", SystemClock.elapsedRealtime() - started in 1800..2800)
    }

    @Test fun foregroundCountdownPausesRestartsCompletesAndUsesSilentNotification() {
        compose.setContent { Text("Timer test") }
        compose.runOnIdle { RestTimerService.start(context, 60) }
        compose.waitUntil(5000) { RestTimerService.state.value.deadline > 0 }
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel("rest")
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        compose.runOnIdle { RestTimerService.command(context, "pause") }
        compose.waitUntil(5000) { RestTimerService.state.value.pausedMs > 0 }
        val paused = RestTimerService.state.value
        assertEquals(paused.pausedMs, paused.remaining(SystemClock.elapsedRealtime() + 300000))
        compose.runOnIdle { RestTimerService.command(context, "add15") }
        compose.waitUntil(5000) { RestTimerService.state.value.pausedMs == paused.pausedMs + 15000 }
        compose.runOnIdle { RestTimerService.command(context, "subtract15") }
        compose.waitUntil(5000) { RestTimerService.state.value.pausedMs == paused.pausedMs }
        compose.runOnIdle { RestTimerService.command(context, "resume") }
        compose.waitUntil(5000) { RestTimerService.state.value.deadline > 0 }
        compose.runOnIdle { RestTimerService.start(context, 1) }
        compose.waitUntil(5000) { !RestTimerService.state.value.active }
        compose.runOnIdle { RestTimerService.start(context, 60) }
        compose.waitUntil(5000) { RestTimerService.state.value.active }
        compose.runOnIdle { RestTimerService.stop(context) }
        compose.waitUntil(5000) { !RestTimerService.state.value.active }
    }
}
