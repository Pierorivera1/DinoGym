package io.github.pierorivera1.dinogym.ui.programs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.ExerciseRepository
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import io.github.pierorivera1.dinogym.di.ApplicationScope
import io.github.pierorivera1.dinogym.ui.components.moved
import kotlinx.coroutines.CoroutineScope
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

/** The editor's own state, next to what the database holds. */
private data class RoutineLocal(
    val expandedId: String? = null,
    val sheet: RoutineSheet? = null,
    val picker: PickerInput? = null,
    val undo: RemovedExercise? = null,
    /**
     * Targets written but maybe not read back yet, so quick taps on + build on each other
     * instead of on the stale database value. An entry goes once the database holds it.
     */
    val targets: Map<String, Target> = emptyMap(),
)

/**
 * The routine editor and its exercise picker. There is no Save button (decision 41): taps
 * are written at once and the name 500 ms after the last key ([NameSaver]). Removing an
 * exercise happens at once with UNDO; deleting the routine asks first.
 *
 * The picker's search and ticks live here, not in the sheet, so they survive a trip to the
 * exercise editor: `+ NEW EXERCISE` creates the exercise, ticks it, then opens its editor.
 */
@HiltViewModel(assistedFactory = RoutineEditViewModel.Factory::class)
class RoutineEditViewModel @AssistedInject constructor(
    @Assisted private val routineId: String,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val programRepository: ProgramRepository,
    private val workoutRepository: WorkoutRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val app: Application,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(routineId: String): RoutineEditViewModel
    }

    private val name = NameSaver(viewModelScope, appScope) { routineRepository.renameRoutine(routineId, it) }
    private val local = MutableStateFlow(RoutineLocal())
    private val writeLock = Mutex()
    private var creating = false

    private val library = exerciseRepository.observeExercises(archived = false)

    val state: StateFlow<RoutineEditUiState> = combine(
        routineRepository.observeRoutine(routineId),
        library,
        name.draft,
        local,
    ) { routine, library, draft, local ->
        RoutineEditMapper.map(routine, draft, local.targets, local.expandedId, local.sheet, local.picker, library, local.undo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditUiState())

    init {
        // Drop each written target once the database reads it back.
        viewModelScope.launch {
            routineRepository.observeRoutine(routineId).collect { detail ->
                val stored = detail?.items.orEmpty().associate { item ->
                    item.routineExercise.let { it.id to Target(it.sets, it.repMin, it.repMax, it.restSeconds) }
                }
                local.update { s -> s.copy(targets = s.targets.filterNot { (id, t) -> stored[id] == t }) }
            }
        }
    }

    fun changeName(text: String) = name.change(text)

    /** Tapping a card opens it in place; tapping the open one (or its ^) closes it. */
    fun toggleExpanded(itemId: String) = local.update { it.copy(expandedId = if (it.expandedId == itemId) null else itemId) }

    fun changeSets(itemId: String, delta: Int) = editTarget(itemId) { RoutineEditRules.withSets(it, delta) }

    fun changeRange(itemId: String, range: Boolean) = editTarget(itemId) { RoutineEditRules.withRange(it, range) }

    fun changeRepMin(itemId: String, delta: Int) = editTarget(itemId) { RoutineEditRules.withRepMin(it, delta) }

    fun changeRepMax(itemId: String, delta: Int) = editTarget(itemId) { RoutineEditRules.withRepMax(it, delta) }

    fun openRest(itemId: String) {
        val item = state.value.items.firstOrNull { it.id == itemId } ?: return
        local.update { it.copy(sheet = RoutineSheet.Rest(itemId, item.name, item.target.restSeconds)) }
    }

    fun saveRest(itemId: String, seconds: Int) {
        closeSheet()
        editTarget(itemId) { it.copy(restSeconds = seconds) }
    }

    fun closeSheet() = local.update { it.copy(sheet = null) }

    /** The trash on an open card: removes the exercise from this routine only, with UNDO. */
    fun removeExercise(itemId: String) {
        val items = state.value.items
        val index = items.indexOfFirst { it.id == itemId }
        if (index < 0) return
        local.update { it.copy(undo = RemovedExercise(itemId, index, items[index].name), expandedId = null) }
        write { routineRepository.removeExercise(itemId) }
    }

    fun undoRemove() {
        val removed = local.value.undo ?: return
        local.update { it.copy(undo = null) }
        write { routineRepository.restoreExercise(routineId, removed.itemId, removed.position) }
    }

    fun dismissUndo() = local.update { it.copy(undo = null) }

    fun moveExercise(from: Int, to: Int) {
        val ids = state.value.items.map { it.id }
        if (from !in ids.indices || to !in ids.indices) return
        write {
            try {
                routineRepository.reorderExercises(routineId, ids.moved(from, to))
            } catch (_: IllegalArgumentException) {
                // The list changed under the drag (a removal not shown yet): drop this move.
            }
        }
    }

    /**
     * The trash in the top bar. A routine whose workout is open cannot be deleted (the workout
     * reads it); otherwise the dialog names the programs whose cycles lose it.
     */
    fun askDelete() {
        viewModelScope.launch {
            val open = workoutRepository.observeInProgressSession().first()
            val sheet = if (open?.routineId == routineId) {
                RoutineSheet.DeleteBlocked
            } else {
                RoutineSheet.ConfirmDelete(programRepository.observeProgramNamesUsingRoutine(routineId).first())
            }
            local.update { it.copy(sheet = sheet) }
        }
    }

    /** DELETE in the dialog: the routine, its exercise list and its cycle steps go (soft). */
    fun delete(onDeleted: () -> Unit) {
        closeSheet()
        write { routineRepository.deleteRoutine(routineId) }
        onDeleted()
    }

    // Exercise picker

    fun openPicker() = local.update { it.copy(picker = PickerInput(), expandedId = null) }

    fun closePicker() = local.update { it.copy(picker = null) }

    fun changeQuery(query: String) = local.update { s -> s.copy(picker = s.picker?.copy(query = query)) }

    fun toggleExercise(exerciseId: String) = local.update { s ->
        val picker = s.picker ?: return@update s
        val selected = if (exerciseId in picker.selected) picker.selected - exerciseId else picker.selected + exerciseId
        s.copy(picker = picker.copy(selected = selected))
    }

    /** ADD n EXERCISES: appends the ticked exercises in tick order, each with its default target. */
    fun addSelected() {
        val picker = local.value.picker ?: return
        local.update { it.copy(picker = null) }
        write {
            val toAdd = PickerRules.toAdd(exerciseRepository.observeExercises(archived = false).first(), picker.selected)
            toAdd.forEach { exercise ->
                val t = RoutineEditRules.defaultTarget(exercise)
                routineRepository.addExercise(routineId, exercise.id, t.sets, t.repMin, t.repMax, t.restSeconds)
            }
        }
    }

    /**
     * `+ NEW EXERCISE "<query>"`: creates the exercise with the search text as its name, ticks
     * it, and calls [onCreated] to open the exercise editor. The picker stays open behind it.
     */
    fun createExercise(onCreated: (exerciseId: String) -> Unit) {
        val picker = local.value.picker ?: return
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                val exerciseName = PickerRules.newExerciseName(picker.query, app.getString(R.string.exercises_new_name))
                val id = exerciseRepository.createDefaultExercise(exerciseName)
                local.update { s -> s.copy(picker = s.picker?.let { it.copy(selected = it.selected + id) }) }
                onCreated(id)
            } finally {
                creating = false
            }
        }
    }

    fun flush() = name.flush()

    override fun onCleared() {
        name.flush()
    }

    private fun editTarget(itemId: String, change: (Target) -> Target) {
        val item = state.value.items.firstOrNull { it.id == itemId } ?: return
        val current = local.value.targets[itemId] ?: item.target
        val next = change(current)
        if (next == current) return
        local.update { it.copy(targets = it.targets + (itemId to next)) }
        write { routineRepository.updateTarget(itemId, next.sets, next.repMin, next.repMax, next.restSeconds) }
    }

    private fun write(block: suspend () -> Unit) {
        appScope.launch { writeLock.withLock { block() } }
    }
}
