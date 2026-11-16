package io.github.pierorivera1.dinogym.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.ExerciseRepository
import io.github.pierorivera1.dinogym.di.ApplicationScope
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Typing is saved this long after the last key press, instead of on every key. */
private const val TYPING_SAVE_DELAY_MS = 500L

/** What the editor knows: still loading, gone, or the exercise being edited. */
private sealed interface EditLoad {
    data object Loading : EditLoad
    data object Missing : EditLoad
    data class Ready(val draft: Exercise) : EditLoad
}

/**
 * The exercise editor. There is no Save button (decision 41): taps (icon, kind, weight,
 * archive) are written at once, and typing is written [TYPING_SAVE_DELAY_MS] after the last
 * key press. When the screen closes, anything still waiting is written straight away.
 *
 * The edits live in a draft copy of the exercise, read once from the database; the database
 * only gets valid values (see [ExerciseEditRules.toStore]). Writes run in the application
 * scope, so the last one still finishes after this ViewModel is cleared.
 */
@HiltViewModel(assistedFactory = ExerciseEditViewModel.Factory::class)
class ExerciseEditViewModel @AssistedInject constructor(
    @Assisted private val exerciseId: String,
    private val exerciseRepository: ExerciseRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(exerciseId: String): ExerciseEditViewModel
    }

    private val load = MutableStateFlow<EditLoad>(EditLoad.Loading)

    /** What the database holds (as far as this screen knows). Read by the write coroutine. */
    @Volatile
    private var saved: Exercise? = null

    /** Writes one at a time, each writing the newest [saved], so an older write never wins. */
    private val writeLock = Mutex()
    private var pendingSave: Job? = null

    val state: StateFlow<ExerciseEditUiState> = combine(
        load,
        exerciseRepository.observeRoutineNamesUsing(exerciseId),
    ) { load, usedIn ->
        when (load) {
            EditLoad.Loading -> ExerciseEditUiState()
            EditLoad.Missing -> ExerciseEditUiState(isLoading = false, isMissing = true)
            is EditLoad.Ready -> ExerciseEditRules.uiState(load.draft, usedIn)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseEditUiState())

    init {
        viewModelScope.launch {
            val exercise = exerciseRepository.observeExercise(exerciseId).first()
            saved = exercise
            load.value = if (exercise == null) EditLoad.Missing else EditLoad.Ready(exercise)
        }
    }

    fun changeName(name: String) = edit(typing = true) { it.copy(name = name) }

    fun changePattern(pattern: String) = edit(typing = true) { it.copy(template = pattern) }

    fun changeIcon(icon: ExerciseIcon) = edit { it.copy(icon = icon) }

    fun changeKind(kind: ExerciseKind) = edit { ExerciseEditRules.withKind(it, kind) }

    fun changeWeightEnabled(enabled: Boolean) = edit { it.copy(weightEnabled = enabled) }

    /** ARCHIVE (true) or RESTORE (false). The exercise and its history stay; it only leaves the picker. */
    fun setArchived(archived: Boolean) {
        val current = (load.value as? EditLoad.Ready)?.draft ?: return
        saveNow()
        load.value = EditLoad.Ready(current.copy(archived = archived))
        saved = saved?.copy(archived = archived)
        appScope.launch { writeLock.withLock { exerciseRepository.setArchived(exerciseId, archived) } }
    }

    /** Saves anything still waiting. Also called when the screen goes to the background. */
    fun flush() = saveNow()

    override fun onCleared() {
        saveNow()
    }

    private fun edit(typing: Boolean = false, change: (Exercise) -> Exercise) {
        load.update { if (it is EditLoad.Ready) EditLoad.Ready(change(it.draft)) else it }
        if (typing) {
            pendingSave?.cancel()
            pendingSave = viewModelScope.launch {
                delay(TYPING_SAVE_DELAY_MS)
                saveNow()
            }
        } else {
            saveNow()
        }
    }

    private fun saveNow() {
        pendingSave?.cancel()
        pendingSave = null
        val draft = (load.value as? EditLoad.Ready)?.draft ?: return
        val base = saved ?: return
        val toStore = ExerciseEditRules.toStore(draft, base)
        if (toStore == base) return
        saved = toStore
        appScope.launch { writeLock.withLock { saved?.let { exerciseRepository.updateExercise(it) } } }
    }
}
