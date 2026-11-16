package io.github.pierorivera1.dinogym.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The active exercise as a navigation entry: connects [WorkoutViewModel] to [WorkoutScreen],
 * turns its events into navigation, and makes system Back act like ✕ (or close a sheet),
 * so a workout is never left by accident.
 */
@Composable
fun WorkoutEntry(
    onOpenRest: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
    onExit: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openRest by rememberUpdatedState(onOpenRest)
    val finished by rememberUpdatedState(onFinished)
    val exit by rememberUpdatedState(onExit)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutEvent.OpenRest -> openRest()
                is WorkoutEvent.Finished -> finished(event.sessionId)
                WorkoutEvent.Exit -> exit()
            }
        }
    }
    BackHandler(onBack = viewModel::onBack)

    WorkoutScreen(
        state = state,
        actions = WorkoutActions(
            onEnd = viewModel::requestEnd,
            onChangeReps = viewModel::changeReps,
            onChangeWeight = viewModel::changeWeight,
            onLogSet = viewModel::logSet,
            onOpenRestPicker = viewModel::openRestPicker,
            onOpenNote = viewModel::openNote,
            onSkip = viewModel::moveOn,
            onOpenSet = viewModel::openSet,
            onCloseSheet = viewModel::closeSheet,
            onSaveRest = viewModel::saveRest,
            onSaveNote = viewModel::saveNote,
            onSaveSet = viewModel::saveSet,
            onDeleteSet = viewModel::deleteSet,
            onSaveAndFinish = viewModel::saveAndFinish,
            onDiscard = viewModel::discard,
        ),
    )
}
