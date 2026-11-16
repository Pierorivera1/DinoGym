package io.github.pierorivera1.dinogym.ui.programs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.di.ApplicationScope
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.ui.components.moved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The program editor. There is no Save button (decision 41): taps are written at once and
 * the name 500 ms after the last key ([NameSaver]). Removing a step happens at once with
 * UNDO; deleting the program asks first.
 *
 * Writes run in the application scope, one at a time, so they finish even if the screen
 * closes right after a tap and never overtake each other.
 */
@HiltViewModel(assistedFactory = ProgramEditViewModel.Factory::class)
class ProgramEditViewModel @AssistedInject constructor(
    @Assisted private val programId: String,
    private val programRepository: ProgramRepository,
    private val routineRepository: RoutineRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val app: Application,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(programId: String): ProgramEditViewModel
    }

    private val name = NameSaver(viewModelScope, appScope) { programRepository.renameProgram(programId, it) }
    private val sheet = MutableStateFlow<ProgramSheet?>(null)
    private val undo = MutableStateFlow<RemovedStep?>(null)
    private val writeLock = Mutex()
    private var creating = false

    val state: StateFlow<ProgramEditUiState> = combine(
        programRepository.observeProgram(programId),
        routineRepository.observeRoutineSummaries(),
        name.draft,
        sheet,
        undo,
    ) { program, routines, draft, sheet, undo ->
        ProgramEditMapper.map(program, draft, routines, sheet, undo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramEditUiState())

    fun changeName(text: String) = name.change(text)

    fun changeType(type: ProgramType) = write { programRepository.setProgramType(programId, type) }

    /** USE THIS PROGRAM: it becomes the only active program, and Home follows it. */
    fun activate() = write { programRepository.setActiveProgram(programId) }

    fun openAddRoutine() {
        sheet.value = ProgramSheet.AddRoutine
    }

    fun askDelete() {
        sheet.value = ProgramSheet.ConfirmDelete
    }

    fun closeSheet() {
        sheet.value = null
    }

    fun addRoutineStep(routineId: String) {
        sheet.value = null
        write { programRepository.addCycleStep(programId, routineId) }
    }

    fun addRestDay() = write { programRepository.addCycleStep(programId, null) }

    /**
     * NEW ROUTINE in the `+ ROUTINE` sheet: creates "New routine", appends it to the cycle and
     * calls [onCreated] so its editor opens. Coming back, the step is already in the cycle.
     */
    fun createRoutineStep(onCreated: (routineId: String) -> Unit) {
        if (creating) return
        creating = true
        sheet.value = null
        viewModelScope.launch {
            try {
                val id = routineRepository.createRoutine(app.getString(R.string.routines_new_name))
                writeLock.withLock { programRepository.addCycleStep(programId, id) }
                onCreated(id)
            } finally {
                creating = false
            }
        }
    }

    /** ✕ on a step: removes it from the cycle (never the routine) and offers UNDO. */
    fun removeStep(stepId: String) {
        val steps = state.value.steps
        val index = steps.indexOfFirst { it.id == stepId }
        if (index < 0) return
        val step = steps[index]
        undo.value = RemovedStep(stepId, index, step.isNext, step.routineName)
        write { programRepository.removeCycleStep(stepId) }
    }

    fun undoRemove() {
        val removed = undo.value ?: return
        undo.value = null
        write { programRepository.restoreCycleStep(programId, removed.stepId, removed.position, removed.wasNext) }
    }

    /** The UNDO bar timed out: the removal stays. */
    fun dismissUndo() {
        undo.value = null
    }

    fun moveStep(from: Int, to: Int) {
        val ids = state.value.steps.map { it.id }
        if (from !in ids.indices || to !in ids.indices) return
        write {
            try {
                programRepository.reorderCycle(programId, ids.moved(from, to))
            } catch (_: IllegalArgumentException) {
                // The cycle changed under the drag (a removal not shown yet): drop this move.
            }
        }
    }

    /** DELETE in the dialog: soft-deletes the program and its cycle, then [onDeleted] leaves the screen. */
    fun delete(onDeleted: () -> Unit) {
        sheet.value = null
        appScope.launch { writeLock.withLock { programRepository.deleteProgram(programId) } }
        onDeleted()
    }

    fun flush() = name.flush()

    override fun onCleared() {
        name.flush()
    }

    private fun write(block: suspend () -> Unit) {
        appScope.launch { writeLock.withLock { block() } }
    }
}
