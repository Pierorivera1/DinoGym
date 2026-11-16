package io.github.pierorivera1.dinogym.ui.bodyweight

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The WEIGHT segment of Stats, ready to drop under the HISTORY / PROGRESS / WEIGHT control:
 * connects [BodyWeightViewModel] to the stateless [BodyWeightPane]. System Back closes an
 * open sheet; otherwise it is left to the Stats screen.
 */
@Composable
fun BodyWeightEntry(
    modifier: Modifier = Modifier,
    viewModel: BodyWeightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.sheet != null, onBack = viewModel::closeSheet)
    BodyWeightPane(state, rememberBodyWeightActions(viewModel), modifier)
}

/** The ViewModel's functions as [BodyWeightActions]. */
@Composable
fun rememberBodyWeightActions(viewModel: BodyWeightViewModel): BodyWeightActions =
    remember(viewModel) {
        BodyWeightActions(
            onSelectRange = viewModel::selectRange,
            onLog = viewModel::openLog,
            onOpenGoal = viewModel::openGoal,
            onDelete = viewModel::delete,
            onUndo = viewModel::undoDelete,
            onUndoTimeout = viewModel::dismissUndo,
            onCloseSheet = viewModel::closeSheet,
            onPreviousDay = viewModel::previousDay,
            onNextDay = viewModel::nextDay,
            onKgTextChange = viewModel::onKgTextChange,
            onNudge = viewModel::nudge,
            onSave = viewModel::save,
            onClearGoal = viewModel::clearGoal,
        )
    }
