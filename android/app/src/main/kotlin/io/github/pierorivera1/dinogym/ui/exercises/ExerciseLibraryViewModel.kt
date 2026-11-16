package io.github.pierorivera1.dinogym.ui.exercises

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The exercise library: active and archived exercises, the filter between them, and
 * `+ NEW`, which creates an exercise with a default name and hands its id to the screen.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val app: Application,
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ACTIVE)

    /** True while `+ NEW` is writing, so a double tap does not create two exercises. */
    private var creating = false

    val state: StateFlow<ExerciseLibraryUiState> = combine(
        filter,
        exerciseRepository.observeExercises(archived = false),
        exerciseRepository.observeExercises(archived = true),
        ExerciseLibraryStateMapper::map,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

    fun selectFilter(newFilter: LibraryFilter) {
        filter.value = newFilter
    }

    /**
     * `+ NEW`: creates "New exercise" and calls [onCreated] with its id to open the editor.
     * The filter goes back to ACTIVE, where the new exercise is listed.
     */
    fun createExercise(onCreated: (exerciseId: String) -> Unit) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                val id = exerciseRepository.createDefaultExercise(app.getString(R.string.exercises_new_name))
                filter.value = LibraryFilter.ACTIVE
                onCreated(id)
            } finally {
                creating = false
            }
        }
    }
}
