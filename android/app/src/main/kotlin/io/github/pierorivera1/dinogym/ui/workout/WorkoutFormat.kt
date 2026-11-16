package io.github.pierorivera1.dinogym.ui.workout

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Number and time formats shared by the workout screens and Home. Plain Kotlin, so they are
 * unit-tested on the JVM. Words (sets, reps) come from string resources; these only build
 * the language-neutral parts.
 */
object WorkoutFormat {

    /** A rep target: `6` for a fixed target, `6–10` for a range. */
    fun reps(repMin: Int, repMax: Int): String = if (repMin >= repMax) "$repMin" else "$repMin–$repMax"

    /** `sets × reps` as on Home's Up next card and the NEXT box: `2 × 6`, `3 × 6–10`. */
    fun setsTimesReps(sets: Int, repMin: Int, repMax: Int): String = "$sets × ${reps(repMin, repMax)}"

    /** A template such as `3s + {n} pulls + 3s` with the number put in: `3s + 4 pulls + 3s`. */
    fun template(template: String, reps: Int): String = template.replace("{n}", reps.toString())

    /** A rest length or a duration under an hour as `m:ss` (`2:30`, `0:45`); longer ones as `h:mm:ss`. */
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.ROOT, minutes, seconds)
        }
    }

    /** The big rest countdown, always two-digit minutes: `01:48`. */
    fun countdown(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(Locale.ROOT, s / 60, s % 60)
    }

    /** Kilograms without a useless `.0`, with the language's decimal mark: `20`, `22.5`, `22,5`. */
    fun kg(weightKg: Double, locale: Locale): String =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(weightKg)

    /** Splits seconds into whole minutes and the remaining seconds, for spoken durations. */
    fun minutesAndSeconds(totalSeconds: Int): Pair<Int, Int> =
        totalSeconds.coerceAtLeast(0).let { it / 60 to it % 60 }
}
