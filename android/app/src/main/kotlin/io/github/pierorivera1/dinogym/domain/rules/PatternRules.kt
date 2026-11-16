package io.github.pierorivera1.dinogym.domain.rules

/**
 * Rules for a pattern exercise ([io.github.pierorivera1.dinogym.domain.model.ExerciseKind.TEMPLATE]),
 * such as `3s + {n} pulls + 3s`: only the number in place of `{n}` is logged.
 */
object PatternRules {

    /** Where the logged number goes. */
    const val PLACEHOLDER = "{n}"

    /** The pattern an exercise gets when it is switched to PATTERN and has none yet. */
    const val DEFAULT = PLACEHOLDER

    /** A pattern is usable only if it says where the number goes. */
    fun isValid(pattern: String?): Boolean = pattern != null && pattern.contains(PLACEHOLDER)

    /** The pattern as it shows in a workout: `3s + {n} pulls + 3s` with 4 is `3s + 4 pulls + 3s`. */
    fun preview(pattern: String, number: Int): String = pattern.replace(PLACEHOLDER, number.toString())
}
