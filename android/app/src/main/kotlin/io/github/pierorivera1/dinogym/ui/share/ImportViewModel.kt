package io.github.pierorivera1.dinogym.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.share.FetchResult
import io.github.pierorivera1.dinogym.data.share.ShareLinks
import io.github.pierorivera1.dinogym.data.share.ShareRepository
import io.github.pierorivera1.dinogym.data.share.SharedContent
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an import landed: the editor to open in place of the import screen. */
sealed interface Imported {
    data class Routine(val routineId: String) : Imported
    data class Program(val programId: String) : Imported
}

/** The import screen (mock `ImportShare.dc.html`). */
data class ImportUiState(
    val step: Step,
    /** The pasted text while [Step.PASTE]; [pasteInvalid] once OPEN found no link in it. */
    val pasted: String = "",
    val pasteInvalid: Boolean = false,
    /** Set in [Step.PREVIEW] and [Step.ADDING]. */
    val content: SharedContent? = null,
    /** Exercises of [content] that will be new in the library, for the NEW tags. */
    val newExercises: Set<ExerciseTemplate> = emptySet(),
    /** Whether a program import becomes the active program (no program is active now). */
    val becomesActive: Boolean = false,
) {
    enum class Step { PASTE, LOADING, OFFLINE, NOT_FOUND, UNREADABLE, FAILED, PREVIEW, ADDING }
}

/**
 * Opens a shared link (task 23, decision 102): reads the share by [shareId] (or, with no id,
 * first asks for a pasted link), shows a preview, and copies it in through the installer on
 * ADD. Reading needs a connection; adding does not.
 */
@HiltViewModel(assistedFactory = ImportViewModel.Factory::class)
class ImportViewModel @AssistedInject constructor(
    @Assisted shareId: String?,
    private val repository: ShareRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(shareId: String?): ImportViewModel
    }

    private var id: String? = shareId
    private val _state = MutableStateFlow(ImportUiState(if (shareId == null) ImportUiState.Step.PASTE else ImportUiState.Step.LOADING))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private var loading: Job? = null

    init {
        if (shareId != null) load()
    }

    fun changePasted(text: String) {
        _state.update { it.copy(pasted = text, pasteInvalid = false) }
    }

    /** OPEN LINK: finds the link in the pasted text and reads it. */
    fun openPasted() {
        val found = ShareLinks.idIn(_state.value.pasted)
        if (found == null) {
            _state.update { it.copy(pasteInvalid = true) }
            return
        }
        id = found
        load()
    }

    fun retry() = load()

    private fun load() {
        val shareId = id ?: return
        loading?.cancel()
        _state.update { it.copy(step = ImportUiState.Step.LOADING) }
        loading = viewModelScope.launch {
            _state.value = when (val result = repository.fetch(shareId)) {
                is FetchResult.Found -> preview(result.content)
                FetchResult.NotFound -> ImportUiState(ImportUiState.Step.NOT_FOUND)
                FetchResult.Offline -> ImportUiState(ImportUiState.Step.OFFLINE)
                FetchResult.Unreadable -> ImportUiState(ImportUiState.Step.UNREADABLE)
                FetchResult.Failed -> ImportUiState(ImportUiState.Step.FAILED)
            }
        }
    }

    private suspend fun preview(content: SharedContent): ImportUiState {
        val routines = when (content) {
            is SharedContent.Routine -> listOf(content.routine)
            is SharedContent.Program -> content.program.routines
        }
        val exercises = routines.flatMap { r -> r.items.map { it.exercise } }
        val known = repository.inLibrary(exercises)
        return ImportUiState(
            step = ImportUiState.Step.PREVIEW,
            content = content,
            newExercises = exercises.filterNotTo(mutableSetOf()) { it in known },
            becomesActive = content is SharedContent.Program && !repository.hasActiveProgram(),
        )
    }

    /** ADD ROUTINE / ADD PROGRAM. [onDone] opens the new item's editor. */
    fun add(onDone: (Imported) -> Unit) {
        val current = _state.value
        val content = current.content ?: return
        if (current.step != ImportUiState.Step.PREVIEW) return
        _state.value = current.copy(step = ImportUiState.Step.ADDING)
        viewModelScope.launch {
            val imported = when (content) {
                is SharedContent.Routine -> Imported.Routine(repository.importRoutine(content.routine))
                is SharedContent.Program -> Imported.Program(repository.importProgram(content.program))
            }
            onDone(imported)
        }
    }
}
