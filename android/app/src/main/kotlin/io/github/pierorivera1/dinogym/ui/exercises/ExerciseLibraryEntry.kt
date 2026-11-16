package io.github.pierorivera1.dinogym.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The library as a navigation entry: connects [ExerciseLibraryViewModel] to the stateless
 * [ExerciseLibraryScreen]. [onOpenExercise] opens the editor; `isNew` is true for an exercise
 * `+ NEW` just created.
 */
@Composable
fun ExerciseLibraryEntry(
    onOpenExercise: (exerciseId: String, isNew: Boolean) -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseLibraryScreen(
        state = state,
        onNew = { viewModel.createExercise(onCreated = { onOpenExercise(it, true) }) },
        onSelectFilter = viewModel::selectFilter,
        onOpenExercise = { onOpenExercise(it, false) },
    )
}
