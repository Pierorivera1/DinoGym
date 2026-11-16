package io.github.pierorivera1.dinogym.ui.exercises

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The library itself is the Exercises tab, ExercisesRoute in ui/navigation/Routes.kt.

/**
 * The exercise editor for [exerciseId]. A pushed screen: not a tab, so the bottom bar is
 * hidden. [isNew] is true right after `+ NEW` created the exercise: the name field opens
 * focused with the default name selected, so typing replaces it. Other screens (the exercise
 * picker) can open it the same way after creating an exercise with
 * `ExerciseRepository.createDefaultExercise`.
 */
@Serializable
data class ExerciseEditRoute(val exerciseId: String, val isNew: Boolean = false) : NavKey
