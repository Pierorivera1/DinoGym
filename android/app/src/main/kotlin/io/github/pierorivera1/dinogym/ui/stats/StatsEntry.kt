package io.github.pierorivera1.dinogym.ui.stats

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightContent
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightOverlays
import io.github.pierorivera1.dinogym.ui.bodyweight.BodyWeightViewModel
import io.github.pierorivera1.dinogym.ui.bodyweight.rememberBodyWeightActions

/**
 * The segments of the Stats tab, in order. To add one (the WEIGHT segment): add an entry
 * here with its label, and its branch in [StatsEntry]'s `when`, which the compiler then asks
 * for. The segmented control takes up to four.
 */
enum class StatsTab(@StringRes val label: Int) {
    HISTORY(R.string.stats_tab_history),
    PROGRESS(R.string.stats_tab_progress),
    WEIGHT(R.string.bw_tab_weight),
}

/**
 * The Stats tab as a navigation entry: the shell ([StatsScreen]) with the selected segment's
 * content. Each segment has its own ViewModel and stateless content composable.
 *
 * WEIGHT also has a sheet and an UNDO bar ([BodyWeightOverlays]). They are drawn over the
 * whole Stats screen, not only its segment, so the scrim covers the title and the control as
 * in the mock; that is why its ViewModel is read here rather than inside the `when`.
 */
@Composable
fun StatsEntry(
    onOpenSession: (sessionId: String) -> Unit,
    onOpenProgress: (exerciseId: String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val weight: BodyWeightViewModel? = if (tab == StatsTab.WEIGHT) hiltViewModel() else null
    val weightState = weight?.let { it.state.collectAsStateWithLifecycle().value }
    val weightActions = weight?.let { rememberBodyWeightActions(it) }
    // System Back closes an open weight sheet first.
    BackHandler(enabled = weightState?.sheet != null) { weight?.closeSheet() }

    Box(Modifier.fillMaxSize()) {
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
                StatsTab.WEIGHT -> if (weightState != null && weightActions != null) {
                    BodyWeightContent(weightState, weightActions, Modifier.weight(1f))
                }
            }
        }
        if (weightState != null && weightActions != null) BodyWeightOverlays(weightState, weightActions)
    }
}
