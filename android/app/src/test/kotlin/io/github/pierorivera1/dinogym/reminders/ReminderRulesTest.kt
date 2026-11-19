package io.github.pierorivera1.dinogym.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReminderRulesTest {

    @Test
    fun `time is stored as HH mm with leading zeros, as the server parses it`() {
        assertEquals("08:05", ReminderRules.encodeTime(LocalTime.of(8, 5)))
        assertEquals("20:00", ReminderRules.encodeTime(LocalTime.of(20, 0)))
    }

    @Test
    fun `missing or damaged time reads as the 8 00 default`() {
        listOf(null, "", "8", "25:00", "08:61", "aa:bb", "08:00:00").forEach {
            assertEquals(it, ReminderRules.DEFAULT_TIME, ReminderRules.decodeTime(it))
        }
        assertEquals(LocalTime.of(6, 30), ReminderRules.decodeTime("06:30"))
    }

    @Test
    fun `switches are on only for the exact text true`() {
        assertTrue(ReminderRules.decodeOn(ReminderRules.encodeOn(true)))
        assertFalse(ReminderRules.decodeOn("false"))
        assertFalse(ReminderRules.decodeOn(null))
        assertFalse(ReminderRules.decodeOn("TRUE"))
    }

    @Test
    fun `picker rounds a minute between steps down and round-trips the steps`() {
        assertEquals(8 to 1, ReminderRules.pickerIndexes(LocalTime.of(8, 7)))
        assertEquals(23 to 11, ReminderRules.pickerIndexes(LocalTime.of(23, 59)))
        assertEquals(LocalTime.of(23, 55), ReminderRules.pickerTime(23, 11))
        assertEquals(LocalTime.of(0, 55), ReminderRules.pickerTime(-1, 99))
    }

    @Test
    fun `zone city is the last part of the id, with spaces`() {
        assertEquals("Lima", ReminderRules.zoneCity("America/Lima"))
        assertEquals("Buenos Aires", ReminderRules.zoneCity("America/Argentina/Buenos_Aires"))
        assertEquals("UTC", ReminderRules.zoneCity("UTC"))
    }
}
