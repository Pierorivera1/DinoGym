package io.github.pierorivera1.dinogym.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineDetail
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.repository.SessionSummary
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the summary screen shows. */
data class SummaryUiState(
    val isLoading: Boolean = true,
    val sessionId: String = "",
    val routineName: String = "",
    val durationSeconds: Long = 0,
    val date: LocalDate? = null,
    val setCount: Int = 0,
    /** Sets the routine plans (show-only items excluded), for `14/14`. */
    val plannedSets: Int = 0,
    val bests: List<SummaryBest> = emptyList(),
    val rows: List<SummaryRow> = emptyList(),
    /** The next cycle step's routine name, or null for a rest day. */
    val nextRoutine: String? = null,
    /** False when the program or its cycle is gone: the "next" line is hidden. */
    val hasNextStep: Boolean = false,
)

/** A new best for the text box: "New best on pull-ups: 7 reps". */
data class SummaryBest(val exerciseName: String, val reps: Int)

/** One line of SET 1 REPS: this session's set 1 against the last session's. */
data class SummaryRow(
    val exerciseName: String,
    /** Null if set 1 was not logged. */
    val reps: Int?,
    /** Reps more (or fewer) than last time; null when there is no last time to compare. */
    val delta: Int?,
)

/** Builds [SummaryUiState]. A plain function, so it is unit-tested. */
object SummaryStateMapper {
    fun map(summary: SessionSummary, routine: RoutineDetail?, zone: ZoneId): SummaryUiState = SummaryUiState(
        isLoading = false,
        sessionId = summary.session.id,
        routineName = summary.routineName,
        durationSeconds = summary.durationMillis / 1000,
        date = Instant.ofEpochMilli(summary.session.startedAt).atZone(zone).toLocalDate(),
        setCount = summary.setCount,
        plannedSets = routine?.items
            ?.filter { it.exercise.kind != ExerciseKind.SHOW_ONLY }
            ?.sumOf { it.routineExercise.sets }
            ?: summary.setCount,
        bests = summary.exercises.mapNotNull { comparison ->
            val setOne = comparison.setOne
            if (comparison.isNewBest && setOne != null) SummaryBest(comparison.exercise.name, setOne.reps) else null
        },
        rows = summary.exercises.map { comparison ->
            val reps = comparison.setOne?.reps
            val previous = comparison.previousSetOne?.reps
            SummaryRow(
                exerciseName = comparison.exercise.name,
                reps = reps,
                delta = if (reps != null && previous != null) reps - previous else null,
            )
        },
        nextRoutine = summary.nextStep?.routineName,
        hasNextStep = summary.nextStep != null,
    )
}

/**
 * The summary after a workout. The session is finished, so the numbers do not change while
 * the screen is open: it reads them once.
 */
@HiltViewModel(assistedFactory = SummaryViewModel.Factory::class)
class SummaryViewModel @AssistedInject constructor(
    @Assisted sessionId: String,
    historyRepository: HistoryRepository,
    routineRepository: RoutineRepository,
) : ViewModel() {

    /** Creates the ViewModel for one session id (the route's argument). */
    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): SummaryViewModel
    }

    val state: StateFlow<SummaryUiState> = flow {
        val summary = historyRepository.getSummary(sessionId)
        if (summary == null) {
            emit(SummaryUiState(isLoading = false, sessionId = sessionId))
            return@flow
        }
        val routine = routineRepository.observeRoutine(summary.session.routineId).first()
        emit(SummaryStateMapper.map(summary, routine, ZoneId.systemDefault()))
    }.stateIn(viewModelScope, SharingStarted.Lazily, SummaryUiState())
}
