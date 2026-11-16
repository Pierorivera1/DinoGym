package io.github.pierorivera1.dinogym.ui.stats

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pierorivera1.dinogym.R

/**
 * The segments of the Stats tab, in order. To add one (the WEIGHT segment): add an entry
 * here with its label, and its branch in [StatsEntry]'s `when`, which the compiler then asks
 * for. The segmented control takes up to four.
 */
enum class StatsTab(@StringRes val label: Int) {
    HISTORY(R.string.stats_tab_history),
    PROGRESS(R.string.stats_tab_progress),
}

/**
 * The Stats tab as a navigation entry: the shell ([StatsScreen]) with the selected segment's
 * content. Each segment has its own ViewModel and stateless content composable.
 */
@Composable
fun StatsEntry(
    onOpenSession: (sessionId: String) -> Unit,
    onOpenProgress: (exerciseId: String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    StatsScreen(tab = tab, onSelectTab = viewModel::selectTab) {
        when (tab) {
            StatsTab.HISTORY -> {
                val history: StatsHistoryViewModel = hiltViewModel()
                val state by history.state.collectAsStateWithLifecycle()
                StatsHistoryContent(state, onOpenSession)
            }
            StatsTab.PROGRESS -> {
                val progress: ProgressListViewModel = hiltViewModel()
                val state by progress.state.collectAsStateWithLifecycle()
                ProgressListContent(state, onOpenProgress)
            }
        }
    }
}
