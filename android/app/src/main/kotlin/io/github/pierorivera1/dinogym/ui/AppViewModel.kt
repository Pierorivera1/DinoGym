package io.github.pierorivera1.dinogym.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import io.github.pierorivera1.dinogym.timer.RestTimer
import io.github.pierorivera1.dinogym.ui.navigation.resumeScreens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app shell's ViewModel (scoped to the activity): what to reopen at launch, and the
 * bottom bar's START, which works from any tab.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    /** The launch check runs once per activity, not again after a rotation. */
    private var resumeChecked = false

    /**
     * The screens to reopen at launch so a workout survives a restart (see [resumeScreens]).
     * Waits for the saved rest to load first. Empty after the first call.
     */
    suspend fun screensToResume(): List<NavKey> {
        if (resumeChecked) return emptyList()
        resumeChecked = true
        restTimer.awaitRestored()
        val inProgress = workoutRepository.observeInProgressSession().first() != null
        return resumeScreens(inProgress, restRunning = restTimer.state.value != null)
    }

    /**
     * START in the bottom bar: the same as Home's START. Opens the next routine (or the
     * workout in progress) through [onStarted]; on a rest day or with no program there is
     * nothing to start, so [onNothingToStart] shows Home, where the rest day can be marked done.
     */
    fun start(onStarted: () -> Unit, onNothingToStart: () -> Unit) {
        viewModelScope.launch {
            if (workoutRepository.startNextWorkout() != null) onStarted() else onNothingToStart()
        }
    }
}
