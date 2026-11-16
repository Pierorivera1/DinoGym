package io.github.pierorivera1.dinogym.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.ActiveWorkout
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import io.github.pierorivera1.dinogym.timer.RestCountdown
import io.github.pierorivera1.dinogym.timer.RestTimer
import io.github.pierorivera1.dinogym.timer.RestTimerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Everything the rest timer screen shows. */
data class RestUiState(
    val isLoading: Boolean = true,
    /** The rest ended or was skipped: the screen goes back to the exercise. */
    val isOver: Boolean = false,
    val routineName: String = "",
    val elapsedSeconds: Long = 0,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val filledBlocks: Int = 0,
    /** What comes next ("Pull-ups, set 2 of 2"), as given to the timer. */
    val nextLabel: String = "",
    /** The set the rest follows, for "set 1 of pull-ups done"; null when unknown. */
    val doneSetIndex: Int? = null,
    val doneExerciseName: String? = null,
    val loggedSetCount: Int = 0,
    val showEndSheet: Boolean = false,
)

/** Builds [RestUiState]. A plain function, so it is unit-tested. */
object RestStateMapper {
    /** [ending] is true while the workout is being saved or discarded from this screen. */
    fun map(rest: RestTimerState?, workout: ActiveWorkout?, nowMillis: Long, showEndSheet: Boolean, ending: Boolean): RestUiState {
        val remaining = rest?.let { RestCountdown.remainingSeconds(it.endsAtMillis, nowMillis) } ?: 0
        val done = workout?.let { w ->
            WorkoutRules.lastDoneSet(w.session.currentPosition, w.exercises.map { it.loggedSets.size })
        }
        return RestUiState(
            isLoading = false,
            // While ending, the screen waits for the summary or Home instead of going back.
            isOver = !ending && (rest == null || remaining == 0),
            routineName = workout?.routine?.name.orEmpty(),
            elapsedSeconds = workout?.let { ((nowMillis - it.session.startedAt) / 1000).coerceAtLeast(0) } ?: 0,
            remainingSeconds = remaining,
            totalSeconds = rest?.totalSeconds ?: 0,
            filledBlocks = rest?.let { RestCountdown.filledBlocks(it.totalSeconds, remaining) } ?: 0,
            nextLabel = rest?.nextLabel.orEmpty(),
            doneSetIndex = done?.second,
            doneExerciseName = done?.let { workout.exercises[it.first].exercise.name },
            loggedSetCount = workout?.exercises?.sumOf { it.loggedSets.size } ?: 0,
            showEndSheet = showEndSheet,
        )
    }
}

/**
 * The rest timer screen. The rest itself lives in [RestTimer] (an end time, a notification
 * countdown and an alarm); this only shows it, once a second while the screen is visible.
 */
@HiltViewModel
class RestViewModel @Inject constructor(
    private val restTimer: RestTimer,
    private val workoutRepository: WorkoutRepository,
    clock: Clock,
) : ViewModel() {

    private val showEnd = MutableStateFlow(false)
    private val ending = MutableStateFlow(false)
    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    private var workout: ActiveWorkout? = null

    /** Navigation requests; the screen collects them once each. */
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    /** After a restart the saved rest loads in the background; wait for it before reading. */
    private val rest: Flow<RestTimerState?> = flow {
        restTimer.awaitRestored()
        emitAll(restTimer.state)
    }

    val state: StateFlow<RestUiState> = combine(
        rest,
        workoutRepository.observeActiveWorkout(),
        secondTicker(clock),
        showEnd,
        ending,
    ) { rest, workout, now, showEnd, ending ->
        this.workout = workout
        RestStateMapper.map(rest, workout, now, showEnd, ending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RestUiState())

    /** −15s / +15s. */
    fun addSeconds(delta: Int) = restTimer.addSeconds(delta)

    /** SKIP REST: the timer clears, the state turns "over", and the screen goes back. */
    fun skip() = restTimer.cancel()

    /** Called when the screen sees the rest is over: clears a rest that reached 0:00 on screen. */
    fun onRestOver() {
        if (restTimer.state.value != null) restTimer.cancel()
    }

    /** True when notifications can show; otherwise the screen asks for the permission. */
    fun notificationsAllowed(): Boolean = restTimer.areNotificationsAllowed()

    /** Permission granted mid-rest: post the countdown notification for the running rest. */
    fun onNotificationsGranted() = restTimer.addSeconds(0)

    fun requestEnd() {
        showEnd.value = true
    }

    fun closeEnd() {
        showEnd.value = false
    }

    fun saveAndFinish() {
        val sessionId = workout?.session?.id ?: return
        showEnd.value = false
        ending.value = true
        restTimer.cancel()
        viewModelScope.launch {
            workoutRepository.finishSession(sessionId)
            eventChannel.send(WorkoutEvent.Finished(sessionId))
        }
    }

    fun discard() {
        val sessionId = workout?.session?.id ?: return
        showEnd.value = false
        ending.value = true
        restTimer.cancel()
        viewModelScope.launch {
            workoutRepository.discardSession(sessionId)
            eventChannel.send(WorkoutEvent.Exit)
        }
    }
}
