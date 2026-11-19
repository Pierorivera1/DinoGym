package io.github.pierorivera1.dinogym.reminders

import io.github.pierorivera1.dinogym.data.repository.SessionListItem
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.ui.home.CycleCell
import io.github.pierorivera1.dinogym.ui.home.HomeExerciseRow
import io.github.pierorivera1.dinogym.ui.home.HomeNext
import io.github.pierorivera1.dinogym.ui.home.HomeProgram
import io.github.pierorivera1.dinogym.ui.home.HomeUiState
import io.github.pierorivera1.dinogym.ui.home.LastWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderContentTest {

    private val zone = ZoneId.of("America/Lima")

    /** Sunday 2026-11-22: the week runs from Monday the 16th. */
    private val sunday = LocalDate.of(2026, 11, 22)

    private fun row(kind: ExerciseKind, sets: Int) = HomeExerciseRow("x", kind, sets, 5, 8, null)

    private val pull = HomeNext.Workout(
        routineId = "pull",
        routineName = "Pull",
        exercises = listOf(row(ExerciseKind.REPS, 4), row(ExerciseKind.REPS, 3), row(ExerciseKind.SHOW_ONLY, 1)),
        inProgress = false,
        lastSetOne = null,
    )

    private fun home(next: HomeNext?, lastOn: LocalDate? = null) = HomeUiState(
        today = sunday,
        program = next?.let { HomeProgram("p", "Mine", 1, 3, listOf(CycleCell("Pull", true)), it) },
        lastWorkout = lastOn?.let { LastWorkout("Legs", it) },
    )

    private fun session(start: LocalDateTime, minutes: Long, sets: Int): SessionListItem {
        val startMillis = start.atZone(zone).toInstant().toEpochMilli()
        return SessionListItem(Session("s$start", "r", "p", startMillis, startMillis + minutes * 60_000, 0), "Legs", sets)
    }

    @Test
    fun `workout day names the routine and counts loggable sets`() {
        assertEquals(ReminderNotice.WorkoutDay("Pull", 3, 7), ReminderContent.workoutDay(home(pull), sunday))
    }

    @Test
    fun `workout day says nothing on a rest day, with no program, mid-workout or after training today`() {
        assertNull(ReminderContent.workoutDay(home(HomeNext.RestDay("Pull")), sunday))
        assertNull(ReminderContent.workoutDay(home(null), sunday))
        assertNull(ReminderContent.workoutDay(home(pull.copy(inProgress = true)), sunday))
        assertNull(ReminderContent.workoutDay(home(pull, lastOn = sunday), sunday))
        assertEquals(ReminderNotice.WorkoutDay("Pull", 3, 7), ReminderContent.workoutDay(home(pull, lastOn = sunday.minusDays(1)), sunday))
    }

    @Test
    fun `weekly counts Monday to today in the phone's zone`() {
        val finished = listOf(
            session(LocalDateTime.of(2026, 11, 22, 9, 0), 70, 20),
            session(LocalDateTime.of(2026, 11, 16, 0, 30), 180, 43),
            // Sunday of the week before: not counted.
            session(LocalDateTime.of(2026, 11, 15, 23, 30), 60, 10),
        )
        assertEquals(ReminderNotice.Week(2, 63, 250, "Pull"), ReminderContent.weekly(home(pull), finished, sunday, zone))
    }

    @Test
    fun `weekly on a rest day names the routine after it, and an empty week nudges`() {
        assertEquals(
            ReminderNotice.EmptyWeek("Legs"),
            ReminderContent.weekly(home(HomeNext.RestDay("Legs")), emptyList(), sunday, zone),
        )
        assertNull(ReminderContent.weekly(home(null), emptyList(), sunday, zone))
        assertEquals(
            ReminderNotice.Week(1, 20, 70, null),
            ReminderContent.weekly(home(null), listOf(session(LocalDateTime.of(2026, 11, 20, 9, 0), 70, 20)), sunday, zone),
        )
    }

    @Test
    fun `wire kinds match the server's`() {
        assertEquals(ReminderKind.WORKOUT_DAY, ReminderKind.fromWire("workout_day"))
        assertEquals(ReminderKind.WEEKLY_SUMMARY, ReminderKind.fromWire("weekly_summary"))
        assertNull(ReminderKind.fromWire("other"))
        assertNull(ReminderKind.fromWire(null))
    }
}
