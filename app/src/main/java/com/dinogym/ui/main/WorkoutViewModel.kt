package com.dinogym.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dinogym.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.dinogym.timer.RestTimerService
import com.dinogym.data.Exercise
import com.dinogym.data.Session
import com.dinogym.data.WorkoutData
import com.dinogym.data.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutState(
    val data: WorkoutData = WorkoutData(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val draft: WorkoutDraft? = null,
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val writes = Mutex()
    private val repository = WorkoutRepository(application.filesDir)
    private val mutableState = MutableStateFlow(WorkoutState())
    val state = mutableState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val data = repository.load()
                val draft = repository.loadDraft()?.takeUnless { d -> !d.editing && data.sessions.any { it.id == d.id } }
                mutableState.value = WorkoutState(data = data, loading = false, draft = draft)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(error = "Couldn't open your local workouts. Your saved file has been kept safe.")
            }
        }
    }

    fun addExercise(exercise: Exercise, onSaved: () -> Unit) = save(
        state.value.data.copy(exercises = state.value.data.exercises + exercise), onSaved,
    )

    fun updateExercise(exercise: Exercise, onSaved: () -> Unit) = save(
        state.value.data.updateExercise(exercise), onSaved,
    )

    fun saveSession(session: Session, onSaved: () -> Unit) = save(
        state.value.data.saveSession(session), onSaved,
    )

    fun deleteSession(sessionId: String, onSaved: () -> Unit) {
        if (state.value.draft?.id == sessionId) showError("Finish or discard your session edits before deleting this workout.")
        else save(state.value.data.deleteSession(sessionId), onSaved)
    }

    fun archiveExercise(exerciseId: String, onSaved: () -> Unit) = save(
        state.value.data.archiveExercise(exerciseId), onSaved,
    )

    fun restoreExercise(exerciseId: String, onSaved: () -> Unit) = save(
        state.value.data.restoreExercise(exerciseId), onSaved,
    )

    fun saveWorkout(workout: WorkoutTemplate, done: () -> Unit) = save(state.value.data.saveWorkout(workout), done)
    fun deleteWorkout(id: String, done: () -> Unit) = save(state.value.data.deleteWorkout(id), done)

    fun saveSplit(split: TrainingSplit, done: () -> Unit) = save(state.value.data.saveSplit(split), done)
    fun selectSplit(id: String) = save(state.value.data.selectSplit(id)) {}

    fun updateDraft(draft: WorkoutDraft) {
        mutableState.value = state.value.copy(draft = draft)
        viewModelScope.launch {
            try { writes.withLock { repository.saveDraft(draft) } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = state.value.copy(error = "Couldn't save your draft. Keep this screen open and try again.")
            }
        }
    }

    fun discardDraft(done: () -> Unit) {
        if (state.value.saving) return
        mutableState.value = state.value.copy(saving = true)
        viewModelScope.launch {
            try {
                writes.withLock { repository.saveDraft(null) }
                RestTimerService.stop(getApplication())
                mutableState.value = state.value.copy(draft = null, saving = false)
                done()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = state.value.copy(saving = false, error = "Couldn't discard your draft. Please try again.")
            }
        }
    }

    fun finishWorkout(session: Session, updateTargets: Boolean, done: () -> Unit) {
        save(state.value.data.finishSession(session, updateTargets, state.value.draft?.exercises.orEmpty().associate { it.exercise.id to it.restSeconds })) {
            RestTimerService.stop(getApplication())
            discardDraft(done)
        }
    }

    private fun save(data: WorkoutData, onSaved: () -> Unit) {
        if (state.value.loading || state.value.saving) return
        mutableState.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                writes.withLock { repository.save(data) }
                mutableState.value = state.value.copy(data = data, loading = false, saving = false, error = null)
                onSaved()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = state.value.copy(saving = false, error = "Couldn't save. Your inputs are still here; please try again.")
            }
        }
    }
    fun showError(message: String) { mutableState.value = state.value.copy(error = message) }
    fun clearError() { mutableState.value = state.value.copy(error = null) }
}
