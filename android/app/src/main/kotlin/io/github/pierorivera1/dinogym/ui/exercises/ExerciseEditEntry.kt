package io.github.pierorivera1.dinogym.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The exercise editor as a navigation entry: creates [ExerciseEditViewModel] for
 * [exerciseId] and shows [ExerciseEditScreen]. System Back and the back button both call
 * [onBack]; the ViewModel writes any pending edit when it is cleared. Edits waiting for the
 * typing delay are also written when the app goes to the background, in case it is killed there.
 */
@Composable
fun ExerciseEditEntry(
    exerciseId: String,
    isNew: Boolean,
    onBack: () -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel<ExerciseEditViewModel, ExerciseEditViewModel.Factory>(
        key = exerciseId,
        creationCallback = { factory -> factory.create(exerciseId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    ExerciseEditScreen(
        state = state,
        actions = ExerciseEditActions(
            onBack = onBack,
            onNameChange = viewModel::changeName,
            onIconChange = viewModel::changeIcon,
            onKindChange = viewModel::changeKind,
            onPatternChange = viewModel::changePattern,
            onWeightChange = viewModel::changeWeightEnabled,
            onArchivedChange = viewModel::setArchived,
        ),
        focusName = isNew,
    )
}
