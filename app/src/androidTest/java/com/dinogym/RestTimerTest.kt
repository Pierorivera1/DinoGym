package com.dinogym

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
