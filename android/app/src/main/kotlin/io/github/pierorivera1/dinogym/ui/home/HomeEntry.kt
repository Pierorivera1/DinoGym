package io.github.pierorivera1.dinogym.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Home as a navigation entry: connects [HomeViewModel] to the stateless [HomeScreen].
 * Navigation itself stays in the app shell, which passes these callbacks.
 */
@Composable
fun HomeEntry(
    onWorkoutStarted: () -> Unit,
    onEditRoutine: (routineId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onStart = { viewModel.start(onWorkoutStarted) },
        onSkip = viewModel::advance,
        onRestDone = viewModel::advance,
        onEditRoutine = onEditRoutine,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
    )
}
