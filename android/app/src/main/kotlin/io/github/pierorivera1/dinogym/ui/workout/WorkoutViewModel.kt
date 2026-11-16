package io.github.pierorivera1.dinogym.ui.workout

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.ActiveWorkout
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.timer.RestTimer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** One-off navigation requests from the workout ViewModels to the screen. */
sealed interface WorkoutEvent {
    /** A rest started: show the rest timer. */
    data object OpenRest : WorkoutEvent

    /** The workout was saved: show its summary. */
    data class Finished(val sessionId: String) : WorkoutEvent

    /** There is no workout any more (discarded, or gone): go Home. */
    data object Exit : WorkoutEvent
}

/** The workout from the repository, with a loading state so "not loaded yet" is not "none". */
internal sealed interface WorkoutLoad {
    data object Loading : WorkoutLoad
    data object Missing : WorkoutLoad
    data class Ready(val workout: ActiveWorkout) : WorkoutLoad
}

/**
 * The active exercise screen. Every logged set is written to Room at once (the repository
 * does it), so the workout survives the app being killed. After a set it starts the rest
 * timer with the exercise's rest time, moves on to the next exercise when the sets are done,
 * and finishes the workout after the last one.
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val restTimer: RestTimer,
    private val app: Application,
    clock: Clock,
) : ViewModel() {

    private val load: StateFlow<WorkoutLoad> = workoutRepository.observeActiveWorkout()
        .map { if (it == null) WorkoutLoad.Missing else WorkoutLoad.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutLoad.Loading)

    private val draft = MutableStateFlow<SetDraft?>(null)
    private val note = MutableStateFlow("")
    private val sheet = MutableStateFlow<WorkoutSheet>(WorkoutSheet.None)

    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)

    /** Navigation requests; the screen collects them once each. */
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    /** True while a write is running, so a double tap does not log two sets. */
    private var busy = false

    /** Set when this screen ends the workout, so the session disappearing is not treated as "gone". */
    private var ending = false

    val state: StateFlow<WorkoutUiState> = combine(load, draft, note, sheet, secondTicker(clock)) { load, draft, note, sheet, now ->
        when (load) {
            is WorkoutLoad.Ready -> WorkoutStateMapper.map(load.workout, now, draft, note, sheet)
            else -> WorkoutUiState(isLoading = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    init {
        viewModelScope.launch {
            load.collect { if (it is WorkoutLoad.Missing && !ending) eventChannel.send(WorkoutEvent.Exit) }
        }
    }

    private val workout: ActiveWorkout? get() = (load.value as? WorkoutLoad.Ready)?.workout

    /** − / + on the reps stepper. Reps never go below 0. */
    fun changeReps(delta: Int) = editDraft { it.copy(reps = (it.reps + delta).coerceAtLeast(0)) }

    /** − / + on the weight stepper, in kilograms. Never below 0. */
    fun changeWeight(deltaKg: Double) = editDraft { it.copy(weightKg = ((it.weightKg ?: 0.0) + deltaKg).coerceAtLeast(0.0)) }

    private fun editDraft(change: (SetDraft) -> SetDraft) {
        val w = workout ?: return
        val ex = w.currentExercise ?: return
        draft.value = change(WorkoutStateMapper.currentValues(w, ex, draft.value))
    }

    /**
     * LOG SET: writes the set, then rests (unless the rest is 0:00 or the workout is over),
     * moving to the next exercise once this one has all its sets.
     */
    fun logSet() {
        val w = workout ?: return
        val ex = w.currentExercise ?: return
        if (ex.exercise.kind == ExerciseKind.SHOW_ONLY) return moveOn()
        if (busy) return
        busy = true
        val values = WorkoutStateMapper.currentValues(w, ex, draft.value)
        viewModelScope.launch {
            try {
                workoutRepository.logSet(w.session.id, ex.exercise.id, values.reps, values.weightKg, note.value.trim().ifEmpty { null })
                draft.value = null
                note.value = ""
                val move = WorkoutRules.afterLog(ex.loggedSets.size + 1, ex.routineExercise.sets, w.session.currentPosition, w.exercises.size)
                if (move.finishes) {
                    finish(w.session.id)
                    return@launch
                }
                if (move.position != w.session.currentPosition) workoutRepository.setCurrentPosition(w.session.id, move.position)
                val rest = ex.routineExercise.restSeconds
                if (rest > 0) {
                    val sameExercise = move.position == w.session.currentPosition
                    restTimer.start(rest, nextLabel(w, move.position, if (sameExercise) ex.loggedSets.size + 2 else 1))
                    eventChannel.send(WorkoutEvent.OpenRest)
                }
            } finally {
                busy = false
            }
        }
    }

    /** SKIP, or DONE on a show-only exercise: next exercise, or finish after the last. */
    fun moveOn() {
        val w = workout ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                draft.value = null
                note.value = ""
                val move = WorkoutRules.moveOn(w.session.currentPosition, w.exercises.size)
                if (move.finishes) finish(w.session.id) else workoutRepository.setCurrentPosition(w.session.id, move.position)
            } finally {
                busy = false
            }
        }
    }

    /** "Pull-ups, set 2 of 2", for the rest notification and the rest screen. */
    private fun nextLabel(w: ActiveWorkout, position: Int, setIndex: Int): String {
        val next = w.exercises.getOrNull(position) ?: return ""
        if (next.exercise.kind == ExerciseKind.SHOW_ONLY) return next.exercise.name
        return app.getString(R.string.rest_next_set, next.exercise.name, setIndex, next.routineExercise.sets)
    }

    fun openRestPicker() {
        val ex = workout?.currentExercise ?: return
        sheet.value = WorkoutSheet.RestPicker(ex.routineExercise.restSeconds)
    }

    /** SAVE in the rest picker: the new rest for this exercise in this routine. */
    fun saveRest(seconds: Int) {
        val ex = workout?.currentExercise ?: return
        sheet.value = WorkoutSheet.None
        viewModelScope.launch { routineRepository.setRestSeconds(ex.routineExercise.id, seconds) }
    }

    fun openNote() {
        sheet.value = WorkoutSheet.Note(note.value)
    }

    /** SAVE in the note sheet: kept until the next set is logged, then saved with it. */
    fun saveNote(text: String) {
        note.value = text
        sheet.value = WorkoutSheet.None
    }

    /** Tapping a done set: opens it to correct or delete. */
    fun openSet(setId: String) {
        val set = workout?.currentExercise?.loggedSets?.firstOrNull { it.id == setId } ?: return
        sheet.value = WorkoutSheet.EditSet(set.id, set.setIndex, set.reps, set.weightKg)
    }

    fun saveSet(setId: String, reps: Int, weightKg: Double?) {
        val set = workout?.currentExercise?.loggedSets?.firstOrNull { it.id == setId }
        sheet.value = WorkoutSheet.None
        viewModelScope.launch { workoutRepository.updateSet(setId, reps, weightKg, set?.note) }
    }

    fun deleteSet(setId: String) {
        sheet.value = WorkoutSheet.None
        viewModelScope.launch { workoutRepository.deleteSet(setId) }
    }

    /** The ✕, and system Back. */
    fun requestEnd() {
        sheet.value = WorkoutSheet.End
    }

    fun closeSheet() {
        sheet.value = WorkoutSheet.None
    }

    /** System Back: closes an open sheet, otherwise asks to end the workout (like ✕). */
    fun onBack() {
        if (sheet.value != WorkoutSheet.None) closeSheet() else requestEnd()
    }

    /** SAVE AND FINISH in the end sheet. */
    fun saveAndFinish() {
        val w = workout ?: return
        sheet.value = WorkoutSheet.None
        viewModelScope.launch { finish(w.session.id) }
    }

    /** DISCARD in the end sheet: the session and its sets are soft-deleted; the cycle stays. */
    fun discard() {
        val w = workout ?: return
        ending = true
        sheet.value = WorkoutSheet.None
        restTimer.cancel()
        viewModelScope.launch {
            workoutRepository.discardSession(w.session.id)
            eventChannel.send(WorkoutEvent.Exit)
        }
    }

    private suspend fun finish(sessionId: String) {
        ending = true
        restTimer.cancel()
        workoutRepository.finishSession(sessionId)
        eventChannel.send(WorkoutEvent.Finished(sessionId))
    }
}
