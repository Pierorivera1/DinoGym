package io.github.pierorivera1.dinogym.ui.stats

import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import io.github.pierorivera1.dinogym.domain.rules.Gain
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Text for the Stats screens that has no words in it, or only the device locale's month and
 * day names. Plain Kotlin, so it is unit-tested. Words come from string resources.
 */
object StatsFormat {

    /** A set-1 result: `8`, or `20 kg × 8` when the exercise logs weight. */
    fun setOne(result: SetOneResult, usesWeight: Boolean, locale: Locale): String =
        if (usesWeight) "${WorkoutFormat.kg(result.weightKg ?: 0.0, locale)} kg × ${result.reps}" else result.reps.toString()

    /** GAIN and a record's delta without the arrow: `+3`, `+2.5 kg`, `0`, `−1`. */
    fun gain(gain: Gain, locale: Locale): String = when (gain) {
        is Gain.Kg -> "+${WorkoutFormat.kg(gain.kg, locale)} kg"
        is Gain.Reps -> when {
            gain.reps > 0 -> "+${gain.reps}"
            gain.reps < 0 -> "−${-gain.reps}"
            else -> "0"
        }
    }

    /** True when [gain] is an improvement (shown in `success`). */
    fun isPositive(gain: Gain): Boolean = when (gain) {
        is Gain.Kg -> gain.kg > 0.0
        is Gain.Reps -> gain.reps > 0
    }

    /** A month heading in history: `NOVEMBER 2026`, `NOVIEMBRE 2026`. */
    fun monthHeading(month: YearMonth, locale: Locale): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).uppercase(locale)

    /** The weekday on a history date tile: `MON`, `LUN`, `SÁB`. */
    fun weekday(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEE", locale).format(date).replace(".", "").uppercase(locale)

    /** A short month for the chart's x axis: `JUL`, `AGO`. */
    fun shortMonth(month: YearMonth, locale: Locale): String =
        DateTimeFormatter.ofPattern("LLL", locale).format(month).replace(".", "").uppercase(locale)

    /** A date in a list: `Mon 16 Nov`, `lun 16 nov`; with the year when it is not [today]'s year. */
    fun listDate(date: LocalDate, today: LocalDate, locale: Locale): String {
        val pattern = if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(date).replace(".", "")
    }

    /** A date read aloud: `July 7, 2026`, `7 de julio de 2026`. */
    fun spokenDate(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)
}
