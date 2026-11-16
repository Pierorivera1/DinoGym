package io.github.pierorivera1.dinogym.data.settings

import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeCodecTest {

    @Test
    fun everyModeRoundTrips() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeModeCodec.decode(ThemeModeCodec.encode(it))) }
    }

    @Test
    fun nothingStoredIsSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeModeCodec.decode(null))
    }

    @Test
    fun unknownValuesFallBackToSystem() {
        listOf("", "AMOLED", "night", "Day ", "1").forEach {
            assertEquals("for \"$it\"", ThemeMode.SYSTEM, ThemeModeCodec.decode(it))
        }
    }
}
