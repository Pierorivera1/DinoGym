package com.dinogym.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkoutRepository(application.filesDir)
    private val mutableState = MutableStateFlow(WorkoutState())
    val state = mutableState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                mutableState.value = WorkoutState(data = repository.load(), loading = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(error = "Couldn't open your local workouts. Your saved file has been kept safe.")
            }
        }
    }

    fun addExercise(exercise: Exercise, onSaved: () -> Unit) = save(
        state.value.data.copy(exercises = state.value.data.exercises + exercise), onSaved,
    )

    fun saveSession(session: Session, onSaved: () -> Unit) = save(
        state.value.data.copy(sessions = state.value.data.sessions + session), onSaved,
    )

    private fun save(data: WorkoutData, onSaved: () -> Unit) {
        if (state.value.loading || state.value.saving) return
        mutableState.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repository.save(data)
                mutableState.value = WorkoutState(data = data, loading = false)
                onSaved()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = state.value.copy(saving = false, error = "Couldn't save. Your inputs are still here; please try again.")
            }
        }
    }
    fun clearError() { mutableState.value = state.value.copy(error = null) }
}
