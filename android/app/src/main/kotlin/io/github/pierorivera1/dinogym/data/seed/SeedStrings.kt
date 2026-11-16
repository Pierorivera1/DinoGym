package io.github.pierorivera1.dinogym.data.seed

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pierorivera1.dinogym.R
import javax.inject.Inject

/**
 * Resolves [SeedText] keys to the phone's language (English or Spanish) from
 * `strings_seed.xml`. The `when` is exhaustive, so a new key does not compile
 * until it has a string.
 */
class SeedStrings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun get(text: SeedText): String = context.getString(resId(text))

    @StringRes
    private fun resId(text: SeedText): Int = when (text) {
        SeedText.PROGRAM_NAME -> R.string.seed_program_name
        SeedText.ROUTINE_PULL -> R.string.seed_routine_pull
        SeedText.ROUTINE_PUSH -> R.string.seed_routine_push
        SeedText.ROUTINE_LEGS -> R.string.seed_routine_legs
        SeedText.ROUTINE_UPPER -> R.string.seed_routine_upper
        SeedText.ROUTINE_LOWER -> R.string.seed_routine_lower
        SeedText.EXERCISE_FRONT_LEVER -> R.string.seed_exercise_front_lever
        SeedText.TEMPLATE_FRONT_LEVER -> R.string.seed_template_front_lever
        SeedText.EXERCISE_FOREARM_CURL -> R.string.seed_exercise_forearm_curl
        SeedText.EXERCISE_PULL_UP -> R.string.seed_exercise_pull_up
        SeedText.EXERCISE_REVERSE_CURL -> R.string.seed_exercise_reverse_curl
        SeedText.EXERCISE_ROW -> R.string.seed_exercise_row
        SeedText.EXERCISE_BICEP_CURL -> R.string.seed_exercise_bicep_curl
        SeedText.EXERCISE_DIP -> R.string.seed_exercise_dip
        SeedText.EXERCISE_SINGLE_ARM_LATERAL_RAISE -> R.string.seed_exercise_single_arm_lateral_raise
        SeedText.EXERCISE_HOLLOW_PUSH_UP -> R.string.seed_exercise_hollow_push_up
        SeedText.EXERCISE_LATERAL_RAISE -> R.string.seed_exercise_lateral_raise
        SeedText.EXERCISE_TRICEPS_PUSH_UP -> R.string.seed_exercise_triceps_push_up
        SeedText.EXERCISE_BULGARIAN_SPLIT_SQUAT -> R.string.seed_exercise_bulgarian_split_squat
        SeedText.EXERCISE_SQUAT -> R.string.seed_exercise_squat
        SeedText.EXERCISE_JUMP_ROPE -> R.string.seed_exercise_jump_rope
        SeedText.EXERCISE_NEUTRAL_GRIP_PULL_UP -> R.string.seed_exercise_neutral_grip_pull_up
        SeedText.EXERCISE_PUSH_UP -> R.string.seed_exercise_push_up
    }
}
