package io.github.pierorivera1.dinogym.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCountdownTest {
    private val now = 1_000_000L

    private fun rest(seconds: Int) = RestCountdown.start(seconds, "Dips", now)!!

    @Test
    fun startSetsTheEndTimeAndTotal() {
        val state = rest(90)
        assertEquals(now + 90_000, state.endsAtMillis)
        assertEquals(90, state.totalSeconds)
        assertEquals("Dips", state.nextLabel)
    }

    @Test
    fun startClampsToNineMinutesAndZeroMeansNoRest() {
        assertEquals(540, RestCountdown.start(600, "", now)!!.totalSeconds)
        assertNull(RestCountdown.start(0, "", now))
        assertNull(RestCountdown.start(-5, "", now))
    }

    @Test
    fun remainingRoundsUpAndNeverGoesNegative() {
        val end = now + 90_000
        assertEquals(90, RestCountdown.remainingSeconds(end, now))
        assertEquals(90, RestCountdown.remainingSeconds(end, now + 1))
        assertEquals(1, RestCountdown.remainingSeconds(end, end - 1))
        assertEquals(0, RestCountdown.remainingSeconds(end, end))
        assertEquals(0, RestCountdown.remainingSeconds(end, end + 5_000))
    }

    @Test
    fun expiredOnceTheEndTimeIsReached() {
        val state = rest(60)
        assertFalse(RestCountdown.isExpired(state, now + 59_999))
        assertTrue(RestCountdown.isExpired(state, now + 60_000))
    }

    @Test
    fun plusAndMinusFifteenMoveTheEndAndTheTotal() {
        val state = rest(90)
        val later = now + 30_000 // 60 s left
        val plus = RestCountdown.addSeconds(state, 15, later)
        assertEquals(state.endsAtMillis + 15_000, plus.endsAtMillis)
        assertEquals(105, plus.totalSeconds)

        val minus = RestCountdown.addSeconds(state, -15, later)
        assertEquals(state.endsAtMillis - 15_000, minus.endsAtMillis)
        assertEquals(75, minus.totalSeconds)
    }

    @Test
    fun minusNeverGoesBelowZeroRemaining() {
        val state = rest(90)
        val later = now + 80_000 // 10 s left
        val result = RestCountdown.addSeconds(state, -15, later)
        assertEquals(0, RestCountdown.remainingSeconds(result.endsAtMillis, later))
        assertTrue(RestCountdown.isExpired(result, later))
        // Only the 10 s that were left came off the total.
        assertEquals(80, result.totalSeconds)
    }

    @Test
    fun plusNeverGoesAboveNineMinutesRemaining() {
        val state = rest(535)
        val result = RestCountdown.addSeconds(state, 15, now)
        assertEquals(540, RestCountdown.remainingSeconds(result.endsAtMillis, now))
        assertEquals(540, result.totalSeconds)
    }

    @Test
    fun plusOnAnExpiredRestCountsFromNow() {
        val state = rest(30)
        val afterEnd = now + 40_000
        val result = RestCountdown.addSeconds(state, 15, afterEnd)
        assertEquals(afterEnd + 15_000, result.endsAtMillis)
    }

    @Test
    fun blocksFollowTheShareStillToGo() {
        assertEquals(10, RestCountdown.filledBlocks(150, 150))
        assertEquals(7, RestCountdown.filledBlocks(150, 108)) // the approved mock: 1:48 of 2:30
        assertEquals(5, RestCountdown.filledBlocks(90, 45))
        assertEquals(0, RestCountdown.filledBlocks(150, 0))
        assertEquals(10, RestCountdown.filledBlocks(60, 90)) // remaining above total is clamped
        assertEquals(0, RestCountdown.filledBlocks(0, 0))
    }
}
