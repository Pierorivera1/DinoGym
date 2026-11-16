package io.github.pierorivera1.dinogym.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Home's state: the active program, the next step of its cycle (or the workout in progress),
 * the next routine's exercises and the last finished workout. Actions start the workout,
 * skip the step, or mark a rest day done.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val programRepository: ProgramRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    historyRepository: HistoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.millis()).atZone(zone).toLocalDate()

    val state: StateFlow<HomeUiState> = combine(
        programRepository.observeActiveProgram(),
        workoutRepository.observeInProgressSession(),
        historyRepository.observeLastFinishedSession(),
    ) { program, inProgress, last -> Triple(program, inProgress, last) }
        .flatMapLatest { (program, inProgress, last) ->
            // The routine to list: the one in progress, else the next step's (null on a rest day).
            val routineId = inProgress?.routineId ?: program?.nextStep?.step?.routineId
            if (routineId == null) {
                flowOf(HomeStateMapper.map(today(), zone, program, inProgress, last, null, emptyMap()))
            } else {
                combine(
                    routineRepository.observeRoutine(routineId),
                    historyRepository.observeLastSetOnes(routineId),
                ) { routine, setOnes ->
                    HomeStateMapper.map(today(), zone, program, inProgress, last, routine, setOnes)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today(), isLoading = true))

    /** START (or RESUME): opens the next routine's workout, then calls [onStarted]. */
    fun start(onStarted: () -> Unit) {
        viewModelScope.launch {
            if (workoutRepository.startNextWorkout() != null) onStarted()
        }
    }

    /** SKIP a routine, or REST DONE on a rest day: both move the cycle on (decision 29). */
    fun advance() {
        val programId = state.value.program?.id ?: return
        viewModelScope.launch { programRepository.advanceCycle(programId) }
    }
}
