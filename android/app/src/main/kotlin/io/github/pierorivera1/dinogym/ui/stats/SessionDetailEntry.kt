package io.github.pierorivera1.dinogym.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The past session as a navigation entry: creates [SessionDetailViewModel] for [sessionId]
 * and shows [SessionDetailScreen]. System Back closes the delete dialog first, then the
 * open stepper, then the screen. [onDeleted] runs after the session is deleted.
 */
@Composable
fun SessionDetailEntry(
    sessionId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenProgress: (exerciseId: String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel<SessionDetailViewModel, SessionDetailViewModel.Factory>(
        key = sessionId,
        creationCallback = { factory -> factory.create(sessionId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.confirmingDelete, onBack = viewModel::cancelDelete)
    BackHandler(enabled = !state.confirmingDelete && state.editing != null, onBack = viewModel::closeEditor)
    SessionDetailScreen(
        state = state,
        actions = SessionDetailActions(
            onBack = onBack,
            onDelete = viewModel::askDelete,
            onConfirmDelete = { viewModel.delete(onDeleted) },
            onCancelDelete = viewModel::cancelDelete,
            onSelectSet = viewModel::selectSet,
            onChangeReps = viewModel::changeReps,
            onChangeWeight = viewModel::changeWeight,
            onOpenProgress = onOpenProgress,
        ),
    )
}
