package com.dinogym

import com.dinogym.timer.RestCountdown
import org.junit.Assert.*
import org.junit.Test

class RestCountdownTest {
    @Test fun adjustmentsKeepRunningOrPausedStateAndRespectBounds() {
        val now = 1000L
        val running = RestCountdown(deadline = now + 60000)
        assertEquals(75000L, running.adjust(15, now).remaining(now))
        assertEquals(45000L, running.adjust(-15, now).remaining(now))
        assertEquals(now, RestCountdown(deadline = now + 1000).adjust(-15, now).deadline)
        val paused = RestCountdown(pausedMs = 60000)
        assertEquals(RestCountdown(pausedMs = 75000), paused.adjust(15, now))
        assertEquals(RestCountdown(), RestCountdown(pausedMs = 1000).adjust(-15, now))
        assertEquals(540000L, RestCountdown(pausedMs = 540000).adjust(15, now).pausedMs)
    }
}
