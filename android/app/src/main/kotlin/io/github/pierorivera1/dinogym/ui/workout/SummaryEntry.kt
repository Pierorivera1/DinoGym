package io.github.pierorivera1.dinogym.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The summary as a navigation entry: creates [SummaryViewModel] for [sessionId] and shows
 * [SummaryScreen]. FINISH goes Home; EDIT is handed to the shell ([onEditSession]), which
 * will open the past-session editor once the Stats screens exist.
 */
@Composable
fun SummaryEntry(
    sessionId: String,
    onFinish: () -> Unit,
    onEditSession: (sessionId: String) -> Unit,
    viewModel: SummaryViewModel = hiltViewModel<SummaryViewModel, SummaryViewModel.Factory>(
        key = sessionId,
        creationCallback = { factory -> factory.create(sessionId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryScreen(state = state, onEdit = { onEditSession(sessionId) }, onFinish = onFinish)
}
