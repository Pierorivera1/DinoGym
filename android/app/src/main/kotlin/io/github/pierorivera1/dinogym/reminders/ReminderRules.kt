package io.github.pierorivera1.dinogym.reminders

import java.time.LocalTime
import java.util.Locale

/**
 * The reminder settings (task 24, decision 105). They are four synced `preference` rows, so
 * they follow the account and the server reconciles the user's schedules from them.
 */
data class ReminderSettings(
    val workoutDay: Boolean = false,
    val weekly: Boolean = false,
    val time: LocalTime = ReminderRules.DEFAULT_TIME,
)

/** The preference keys. The server reads the same four (backend/internal/reminders). */
object ReminderKeys {
    const val WORKOUT_DAY = "reminder_workout_day"
    const val WEEKLY = "reminder_weekly"
    const val TIME = "reminder_time"
    const val TIME_ZONE = "reminder_time_zone"
}

/** How the settings are stored and picked. Plain functions, unit-tested. */
object ReminderRules {
    val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

    /** The weekly summary's fixed time, Sundays (decision 104). */
    val WEEKLY_TIME: LocalTime = LocalTime.of(20, 0)

    /** The time sheet's wheels: every hour, minutes in 5-minute steps. */
    val HOURS: List<Int> = (0..23).toList()
    val MINUTES: List<Int> = (0..55 step 5).toList()

    fun encodeOn(on: Boolean): String = on.toString()

    fun decodeOn(stored: String?): Boolean = stored == "true"

    /** `HH:mm`, as the server parses it. */
    fun encodeTime(time: LocalTime): String = String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)

    /** A stored `HH:mm`, or the default when it is missing or unreadable. */
    fun decodeTime(stored: String?): LocalTime {
        val parts = stored?.split(":") ?: return DEFAULT_TIME
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return DEFAULT_TIME
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return DEFAULT_TIME
        if (parts.size != 2 || hour !in 0..23 || minute !in 0..59) return DEFAULT_TIME
        return LocalTime.of(hour, minute)
    }

    /** The wheels' indexes for [time]; a minute between steps shows the step before it. */
    fun pickerIndexes(time: LocalTime): Pair<Int, Int> = time.hour to (time.minute / 5).coerceIn(0, MINUTES.lastIndex)

    fun pickerTime(hourIndex: Int, minuteIndex: Int): LocalTime =
        LocalTime.of(HOURS[hourIndex.coerceIn(0, HOURS.lastIndex)], MINUTES[minuteIndex.coerceIn(0, MINUTES.lastIndex)])

    /** The city part of a zone id for the sheet's note: `America/Lima` → `Lima`. */
    fun zoneCity(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')
}
