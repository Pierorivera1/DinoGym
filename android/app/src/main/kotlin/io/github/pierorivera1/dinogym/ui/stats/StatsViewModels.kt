package io.github.pierorivera1.dinogym.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.ExerciseRepository
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/**
 * The Stats tab shell: which segment is selected. It lives as long as the Stats entry, so
 * coming back from a past session or a progress view keeps the segment.
 */
@HiltViewModel
class StatsViewModel @Inject constructor() : ViewModel() {
    private val _tab = MutableStateFlow(StatsTab.HISTORY)
    val tab: StateFlow<StatsTab> = _tab.asStateFlow()

    fun selectTab(tab: StatsTab) {
        _tab.value = tab
    }
}

/** The HISTORY segment: finished sessions by month, with their new-best counts. */
@HiltViewModel
class StatsHistoryViewModel @Inject constructor(historyRepository: HistoryRepository) : ViewModel() {
    val state: StateFlow<StatsHistoryUiState> = combine(
        historyRepository.observeFinishedSessions(),
        historyRepository.observeNewBestsBySession(),
    ) { sessions, newBests ->
        StatsHistoryMapper.map(sessions, newBests, ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsHistoryUiState())
}

/** The PROGRESS segment: active exercises with their best set 1. */
@HiltViewModel
class ProgressListViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    historyRepository: HistoryRepository,
) : ViewModel() {
    val state: StateFlow<ProgressListUiState> = combine(
        exerciseRepository.observeExercises(archived = false),
        historyRepository.observeBestSetOnes(),
        ProgressListMapper::map,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressListUiState())
}
