package io.github.pierorivera1.dinogym.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReorderTest {

    private val list = listOf("Pull", "Push", "Legs", "Rest", "Upper")

    @Test
    fun movedDownShiftsTheItemsBetweenUp() {
        assertEquals(listOf("Push", "Legs", "Pull", "Rest", "Upper"), list.moved(0, 2))
    }

    @Test
    fun movedUpShiftsTheItemsBetweenDown() {
        assertEquals(listOf("Pull", "Upper", "Push", "Legs", "Rest"), list.moved(4, 1))
    }

    @Test
    fun movedToTheSamePlaceReturnsTheList() {
        assertSame(list, list.moved(2, 2))
    }

    @Test
    fun swapsWithTheNextRowPastHalfItsHeight() {
        assertEquals(0, reorderStep(offset = 30f, previousSize = 60f, nextSize = 60f))
        assertEquals(1, reorderStep(offset = 31f, previousSize = 60f, nextSize = 60f))
    }

    @Test
    fun swapsWithThePreviousRowPastHalfItsHeight() {
        assertEquals(0, reorderStep(offset = -50f, previousSize = 120f, nextSize = 60f))
        assertEquals(-1, reorderStep(offset = -61f, previousSize = 120f, nextSize = 60f))
    }

    @Test
    fun neverSwapsPastTheEnds() {
        assertEquals(0, reorderStep(offset = 500f, previousSize = 60f, nextSize = null))
        assertEquals(0, reorderStep(offset = -500f, previousSize = null, nextSize = 60f))
    }
}
