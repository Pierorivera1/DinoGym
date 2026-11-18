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
        SeedText.STARTER_GYM_5 -> R.string.seed_starter_gym_5
        SeedText.STARTER_GYM_4 -> R.string.seed_starter_gym_4
        SeedText.STARTER_CALISTHENICS_5 -> R.string.seed_starter_calisthenics_5
        SeedText.STARTER_CALISTHENICS_4 -> R.string.seed_starter_calisthenics_4
        SeedText.EXERCISE_INCLINE_DUMBBELL_PRESS -> R.string.seed_exercise_incline_dumbbell_press
        SeedText.EXERCISE_CHEST_SUPPORTED_ROW -> R.string.seed_exercise_chest_supported_row
        SeedText.EXERCISE_LAT_PULLDOWN -> R.string.seed_exercise_lat_pulldown
        SeedText.EXERCISE_CABLE_LATERAL_RAISE -> R.string.seed_exercise_cable_lateral_raise
        SeedText.EXERCISE_OVERHEAD_TRICEPS_EXTENSION -> R.string.seed_exercise_overhead_triceps_extension
        SeedText.EXERCISE_HACK_SQUAT -> R.string.seed_exercise_hack_squat
        SeedText.EXERCISE_ROMANIAN_DEADLIFT -> R.string.seed_exercise_romanian_deadlift
        SeedText.EXERCISE_SEATED_LEG_CURL -> R.string.seed_exercise_seated_leg_curl
        SeedText.EXERCISE_LEG_EXTENSION -> R.string.seed_exercise_leg_extension
        SeedText.EXERCISE_STANDING_CALF_RAISE -> R.string.seed_exercise_standing_calf_raise
        SeedText.EXERCISE_MACHINE_CHEST_PRESS -> R.string.seed_exercise_machine_chest_press
        SeedText.EXERCISE_SEATED_CABLE_FLY -> R.string.seed_exercise_seated_cable_fly
        SeedText.EXERCISE_SKULLCRUSHER -> R.string.seed_exercise_skullcrusher
        SeedText.EXERCISE_ONE_ARM_DUMBBELL_ROW -> R.string.seed_exercise_one_arm_dumbbell_row
        SeedText.EXERCISE_REVERSE_PEC_DECK -> R.string.seed_exercise_reverse_pec_deck
        SeedText.EXERCISE_PREACHER_CURL -> R.string.seed_exercise_preacher_curl
        SeedText.EXERCISE_LEG_PRESS -> R.string.seed_exercise_leg_press
        SeedText.EXERCISE_BACK_EXTENSION -> R.string.seed_exercise_back_extension
        SeedText.EXERCISE_SEATED_CALF_RAISE -> R.string.seed_exercise_seated_calf_raise
        SeedText.EXERCISE_PIKE_PUSH_UP -> R.string.seed_exercise_pike_push_up
        SeedText.EXERCISE_NORDIC_CURL_NEGATIVE -> R.string.seed_exercise_nordic_curl_negative
        SeedText.EXERCISE_SINGLE_LEG_HIP_THRUST -> R.string.seed_exercise_single_leg_hip_thrust
        SeedText.EXERCISE_SINGLE_LEG_CALF_RAISE -> R.string.seed_exercise_single_leg_calf_raise
        SeedText.EXERCISE_DIAMOND_PUSH_UP -> R.string.seed_exercise_diamond_push_up
        SeedText.EXERCISE_INVERTED_ROW -> R.string.seed_exercise_inverted_row
        SeedText.EXERCISE_WALKING_LUNGE -> R.string.seed_exercise_walking_lunge
    }
}
