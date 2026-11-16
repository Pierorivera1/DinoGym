package io.github.pierorivera1.dinogym.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The progress view as a navigation entry: [ProgressViewModel] for [exerciseId] and [ProgressScreen]. */
@Composable
fun ProgressEntry(
    exerciseId: String,
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel<ProgressViewModel, ProgressViewModel.Factory>(
        key = exerciseId,
        creationCallback = { factory -> factory.create(exerciseId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(state = state, onBack = onBack)
}
