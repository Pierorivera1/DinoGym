package com.dinogym

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class Workout(val routine: String) : NavKey
@Serializable data class EditWorkout(val sessionId: String) : NavKey
@Serializable data class ExerciseProgress(val exerciseId: String) : NavKey
@Serializable data object History : NavKey

@Serializable data object Plans : NavKey
