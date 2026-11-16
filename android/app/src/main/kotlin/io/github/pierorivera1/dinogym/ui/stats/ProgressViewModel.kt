package io.github.pierorivera1.dinogym.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.ExerciseRepository
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.rules.ExerciseProgress
import io.github.pierorivera1.dinogym.domain.rules.ProgressRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Everything the progress view of one exercise shows. */
data class ProgressUiState(
    val isLoading: Boolean = true,
    /** The exercise does not exist. Archived exercises still have their progress (decision 41). */
    val isMissing: Boolean = false,
    val name: String = "",
    val icon: ExerciseIcon = ExerciseIcon.GENERIC,
    /** Records, best, gain. No records means the exercise was never logged: the empty state. */
    val progress: ExerciseProgress = ProgressRules.progress(emptyList()),
    /** Today in the device's zone: the chart's x axis runs to this month, record dates show a year when it differs. */
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val zone: ZoneId = ZoneId.of("UTC"),
)

/** The progress view of one exercise: its set-1 history, live, so edits to past sessions show at once. */
@HiltViewModel(assistedFactory = ProgressViewModel.Factory::class)
class ProgressViewModel @AssistedInject constructor(
    @Assisted exerciseId: String,
    exerciseRepository: ExerciseRepository,
    historyRepository: HistoryRepository,
    clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(exerciseId: String): ProgressViewModel
    }

    val state: StateFlow<ProgressUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        historyRepository.observeSetOneHistory(exerciseId),
    ) { exercise, history ->
        if (exercise == null) {
            ProgressUiState(isLoading = false, isMissing = true)
        } else {
            val zone = ZoneId.systemDefault()
            ProgressUiState(
                isLoading = false,
                name = exercise.name,
                icon = exercise.icon,
                progress = ProgressRules.progress(history),
                today = LocalDate.now(clock.withZone(zone)),
                zone = zone,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
