package io.github.pierorivera1.dinogym.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import io.github.pierorivera1.dinogym.di.ApplicationScope
import io.github.pierorivera1.dinogym.domain.model.SetValues
import io.github.pierorivera1.dinogym.ui.workout.WEIGHT_STEP_KG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * A past session. Tapping a set chip opens a stepper; every − / + is written at once
 * (decision 41, no Save button), and progress follows because it is a query over set 1.
 * Delete asks first; the program cycle does not move back.
 *
 * Writes run in the application scope, one at a time, and each writes the newest values of
 * its set, so a fast run of taps never ends with an older value stored.
 */
@HiltViewModel(assistedFactory = SessionDetailViewModel.Factory::class)
class SessionDetailViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val historyRepository: HistoryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): SessionDetailViewModel
    }

    private val editing = MutableStateFlow<EditingSet?>(null)
    private val confirmingDelete = MutableStateFlow(false)

    /** Values waiting to be written, by set id. A write takes the newest and removes it. */
    private val pending = ConcurrentHashMap<String, SetValues>()
    private val writeLock = Mutex()

    val state: StateFlow<SessionDetailUiState> = combine(
        historyRepository.observeSessionDetail(sessionId),
        historyRepository.observeNewBestsBySession().map { it[sessionId].orEmpty() },
        editing,
        confirmingDelete,
    ) { detail, newBests, editing, confirming ->
        SessionDetailMapper.map(detail, newBests, ZoneId.systemDefault()).copy(
            // A set that was deleted elsewhere closes its stepper.
            editing = editing?.takeIf { e -> detail?.exercises?.any { ex -> ex.sets.any { it.id == e.setId } } == true },
            confirmingDelete = confirming,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    /** Opens the stepper for a chip, or closes it when the chip is already open. */
    fun selectSet(setId: String) {
        if (editing.value?.setId == setId) {
            editing.value = null
            return
        }
        val exercise = state.value.exercises.firstOrNull { ex -> ex.sets.any { it.setId == setId } } ?: return
        val chip = exercise.sets.first { it.setId == setId }
        editing.value = EditingSet(setId, exercise.exerciseId, chip.setIndex, exercise.sets.size, chip.reps, chip.weightKg)
    }

    fun closeEditor() {
        editing.value = null
    }

    /** − / + on the reps. Never below 0, like the workout. */
    fun changeReps(delta: Int) = edit { it.copy(reps = (it.reps + delta).coerceAtLeast(0)) }

    /** − / + on the weight, in plate steps. Only for a set logged with weight. */
    fun changeWeight(steps: Int) = edit { e ->
        e.weightKg?.let { e.copy(weightKg = (it + steps * WEIGHT_STEP_KG).coerceAtLeast(0.0)) } ?: e
    }

    fun askDelete() {
        editing.value = null
        confirmingDelete.value = true
    }

    fun cancelDelete() {
        confirmingDelete.value = false
    }

    /** Deletes the session (soft) and then calls [onDeleted], which leaves the screen. */
    fun delete(onDeleted: () -> Unit) {
        confirmingDelete.value = false
        viewModelScope.launch {
            historyRepository.deleteSession(sessionId)
            onDeleted()
        }
    }

    private fun edit(change: (EditingSet) -> EditingSet) {
        val before = editing.value ?: return
        val after = change(before)
        if (after == before) return
        editing.value = after
        pending[after.setId] = SetValues(after.reps, after.weightKg)
        appScope.launch {
            writeLock.withLock {
                pending.remove(after.setId)?.let { historyRepository.updateSetValues(after.setId, it.reps, it.weightKg) }
            }
        }
    }
}
