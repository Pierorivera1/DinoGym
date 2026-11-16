package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.ProgramType

/**
 * Whether the weight input shows for an exercise (decision 26): always in a Gym
 * program; in a Calisthenics program only when the exercise has its weight switch on
 * (for example weighted dips).
 */
object WeightField {
    fun isShown(programType: ProgramType, exerciseWeightEnabled: Boolean): Boolean =
        programType == ProgramType.GYM || exerciseWeightEnabled
}
