package io.github.pierorivera1.dinogym.reminders

import io.github.pierorivera1.dinogym.data.repository.SessionListItem
import io.github.pierorivera1.dinogym.ui.home.HomeNext
import io.github.pierorivera1.dinogym.ui.home.HomeUiState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The server's message kinds (decision 104). */
enum class ReminderKind(val wire: String) {
    WORKOUT_DAY("workout_day"),
    WEEKLY_SUMMARY("weekly_summary"),
    ;

    companion object {
        fun fromWire(value: String?): ReminderKind? = entries.firstOrNull { it.wire == value }
    }
}

/** What a reminder says, before it is put into words from the string resources. */
sealed interface ReminderNotice {
    /** "Today is Pull day" / "5 exercises · 17 sets. Tap to start." */
    data class WorkoutDay(val routineName: String, val exercises: Int, val sets: Int) : ReminderNotice

    /** "Your week: 4 workouts" / "63 sets in 4 h 10 min. Up next: Legs." */
    data class Week(val workouts: Int, val sets: Int, val minutes: Long, val upNext: String?) : ReminderNotice

    /** "No workouts this week" / "Your Pull day is waiting." */
    data class EmptyWeek(val upNext: String) : ReminderNotice
}

/**
 * Decides what a reminder shows from the phone's own data (decision 104). Plain functions
 * over Home's state and the finished sessions, so they are unit-tested. Null means show
 * nothing.
 */
object ReminderContent {

    /**
     * The routine up next. Nothing with no active program, on a rest day, during a workout,
     * or when a workout was already finished [today].
     */
    fun workoutDay(home: HomeUiState, today: LocalDate): ReminderNotice? {
        val next = home.program?.next as? HomeNext.Workout ?: return null
        if (next.inProgress || home.lastWorkout?.date == today) return null
        return ReminderNotice.WorkoutDay(next.routineName, next.exercises.size, next.totalSets)
    }

    /**
     * The week from Monday to [today] in [zone]: workouts finished, sets and time trained,
     * and the routine up next. With no workouts and nothing up next there is nothing to say.
     */
    fun weekly(home: HomeUiState, finished: List<SessionListItem>, today: LocalDate, zone: ZoneId): ReminderNotice? {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = finished.filter {
            val day = Instant.ofEpochMilli(it.session.startedAt).atZone(zone).toLocalDate()
            !day.isBefore(weekStart) && !day.isAfter(today)
        }
        val upNext = when (val next = home.program?.next) {
            is HomeNext.Workout -> next.routineName
            is HomeNext.RestDay -> next.followingRoutine
            else -> null
        }
        if (week.isEmpty()) return upNext?.let { ReminderNotice.EmptyWeek(it) }
        val millis = week.sumOf { item -> item.session.endedAt?.let { (it - item.session.startedAt).coerceAtLeast(0) } ?: 0L }
        return ReminderNotice.Week(
            workouts = week.size,
            sets = week.sumOf { it.setCount },
            minutes = millis / 60_000,
            upNext = upNext,
        )
    }
}
