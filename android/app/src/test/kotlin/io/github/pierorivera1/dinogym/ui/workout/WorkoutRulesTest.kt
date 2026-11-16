package io.github.pierorivera1.dinogym.ui.workout

import io.github.pierorivera1.dinogym.ui.navigation.RestRoute
import io.github.pierorivera1.dinogym.ui.navigation.WorkoutRoute
import io.github.pierorivera1.dinogym.ui.navigation.resumeScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** The workout flow's small rules and formats: plain JVM tests. */
class WorkoutRulesTest {

    @Test
    fun `logging a set before the last stays on the exercise`() {
        assertEquals(NextMove(position = 2, finishes = false), WorkoutRules.afterLog(setsDone = 1, targetSets = 2, position = 2, exerciseCount = 6))
    }

    @Test
    fun `logging the last set moves to the next exercise`() {
        assertEquals(NextMove(position = 3, finishes = false), WorkoutRules.afterLog(setsDone = 2, targetSets = 2, position = 2, exerciseCount = 6))
    }

    @Test
    fun `logging the last set of the last exercise finishes`() {
        assertEquals(true, WorkoutRules.afterLog(setsDone = 2, targetSets = 2, position = 5, exerciseCount = 6).finishes)
    }

    @Test
    fun `skipping the last exercise finishes`() {
        assertEquals(true, WorkoutRules.moveOn(position = 5, exerciseCount = 6).finishes)
        assertEquals(NextMove(1, false), WorkoutRules.moveOn(position = 0, exerciseCount = 6))
    }

    @Test
    fun `last done set is on this exercise, else the previous one`() {
        assertEquals(2 to 1, WorkoutRules.lastDoneSet(position = 2, loggedCounts = listOf(2, 3, 1, 0)))
        assertEquals(1 to 3, WorkoutRules.lastDoneSet(position = 2, loggedCounts = listOf(2, 3, 0, 0)))
        assertNull(WorkoutRules.lastDoneSet(position = 0, loggedCounts = listOf(0, 0)))
    }

    @Test
    fun `picker splits and joins rest times, rounding to 5 s and capping at 9 00`() {
        assertEquals(2 to 6, WorkoutRules.pickerIndexes(150))
        assertEquals(1 to 6, WorkoutRules.pickerIndexes(92))
        assertEquals(150, WorkoutRules.pickerSeconds(2, 6))
        assertEquals(540, WorkoutRules.pickerSeconds(9, 11))
        assertEquals(9 to 0, WorkoutRules.pickerIndexes(10_000))
    }

    @Test
    fun `formats sets times reps, templates and times`() {
        assertEquals("2 × 6", WorkoutFormat.setsTimesReps(2, 6, 6))
        assertEquals("3 × 6–10", WorkoutFormat.setsTimesReps(3, 6, 10))
        assertEquals("3s + 4 pulls + 3s", WorkoutFormat.template("3s + {n} pulls + 3s", 4))
        assertEquals("2:30", WorkoutFormat.clock(150))
        assertEquals("42:10", WorkoutFormat.clock(2530))
        assertEquals("1:02:05", WorkoutFormat.clock(3725))
        assertEquals("01:48", WorkoutFormat.countdown(108))
        assertEquals("22.5", WorkoutFormat.kg(22.5, Locale.ENGLISH))
        assertEquals("22,5", WorkoutFormat.kg(22.5, Locale.forLanguageTag("es")))
        assertEquals("20", WorkoutFormat.kg(20.0, Locale.ENGLISH))
    }

    @Test
    fun `summary delta text`() {
        assertEquals("=", deltaText(0))
        assertEquals("▲ +1", deltaText(1))
        assertEquals("−2", deltaText(-2))
        assertEquals("–", deltaText(null))
    }

    @Test
    fun `launch reopens the workout and a running rest`() {
        assertEquals(emptyList<Any>(), resumeScreens(workoutInProgress = false, restRunning = true))
        assertEquals(listOf(WorkoutRoute), resumeScreens(workoutInProgress = true, restRunning = false))
        assertEquals(listOf(WorkoutRoute, RestRoute), resumeScreens(workoutInProgress = true, restRunning = true))
    }
}
