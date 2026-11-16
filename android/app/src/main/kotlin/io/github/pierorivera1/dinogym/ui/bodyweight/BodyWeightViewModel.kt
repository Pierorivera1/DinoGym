package io.github.pierorivera1.dinogym.ui.bodyweight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.repository.BodyWeightRepository
import io.github.pierorivera1.dinogym.domain.rules.BodyWeightRules
import io.github.pierorivera1.dinogym.domain.rules.WeightRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * The WEIGHT segment of Stats (task 11): the chart range, the log and goal sheets, and
 * delete with UNDO. Everything is stored in Room at once (offline first).
 */
@HiltViewModel
class BodyWeightViewModel @Inject constructor(
    private val repository: BodyWeightRepository,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val locale: Locale get() = Locale.getDefault()
    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    private val range = MutableStateFlow(WeightRange.THREE_MONTHS)
    private val sheet = MutableStateFlow<WeightSheet?>(null)
    private val undo = MutableStateFlow<WeighInRow?>(null)

    /** True while a save is writing, so a double tap does not store two weigh-ins. */
    private var saving = false

    val state: StateFlow<BodyWeightUiState> = combine(
        repository.observeWeighIns(),
        repository.observeGoalKg(),
        range,
        sheet,
        undo,
    ) { weighIns, goalKg, range, sheet, undo ->
        BodyWeightStateMapper.map(weighIns, goalKg, range, today(), zone).copy(sheet = sheet, undo = undo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyWeightUiState())

    fun selectRange(newRange: WeightRange) {
        range.value = newRange
    }

    /** LOG WEIGHT: opens the sheet for today, starting at the latest weigh-in. */
    fun openLog() {
        viewModelScope.launch {
            val start = repository.latestWeightKg() ?: BodyWeightStateMapper.DEFAULT_START_KG
            val today = today()
            sheet.value = WeightSheet.Log(day = today, today = today, text = BodyWeightStateMapper.kgText(start, locale))
        }
    }

    /** The GOAL panel: opens the goal sheet at the goal, or at the current average without one. */
    fun openGoal() {
        val current = state.value
        val start = current.goalKg ?: current.currentKg ?: BodyWeightStateMapper.DEFAULT_START_KG
        sheet.value = WeightSheet.Goal(text = BodyWeightStateMapper.kgText(start, locale), hasGoal = current.goalKg != null)
    }

    fun closeSheet() {
        sheet.value = null
    }

    fun previousDay() = updateLog { it.copy(day = it.day.minusDays(1)) }

    fun nextDay() = updateLog { if (it.canGoForward) it.copy(day = it.day.plusDays(1)) else it }

    private fun updateLog(change: (WeightSheet.Log) -> WeightSheet.Log) = sheet.update { if (it is WeightSheet.Log) change(it) else it }

    fun onKgTextChange(text: String) = sheet.update {
        when (it) {
            is WeightSheet.Log -> it.copy(text = text, showError = false)
            is WeightSheet.Goal -> it.copy(text = text, showError = false)
            null -> null
        }
    }

    /** − ([steps] = -1) or + (1): 0.1 kg from the typed value (or from where the sheet started if it does not parse). */
    fun nudge(steps: Int) = sheet.update { open ->
        open ?: return@update null
        val base = BodyWeightRules.parseKg(open.text) ?: state.value.currentKg ?: BodyWeightStateMapper.DEFAULT_START_KG
        val text = BodyWeightStateMapper.kgText(BodyWeightRules.nudge(base, steps), locale)
        when (open) {
            is WeightSheet.Log -> open.copy(text = text, showError = false)
            is WeightSheet.Goal -> open.copy(text = text, showError = false)
        }
    }

    /** SAVE in either sheet. An invalid weight keeps the sheet open with an error. */
    fun save() {
        val open = sheet.value ?: return
        val kg = BodyWeightRules.parseKg(open.text)
        if (kg == null) {
            sheet.value = when (open) {
                is WeightSheet.Log -> open.copy(showError = true)
                is WeightSheet.Goal -> open.copy(showError = true)
            }
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                when (open) {
                    is WeightSheet.Log -> repository.addWeighIn(kg, open.day, zone)
                    is WeightSheet.Goal -> repository.setGoalKg(kg)
                }
                sheet.value = null
            } finally {
                saving = false
            }
        }
    }

    fun clearGoal() {
        viewModelScope.launch {
            repository.setGoalKg(null)
            sheet.value = null
        }
    }

    /** Deletes at once (soft) and shows UNDO (decision 41). */
    fun delete(row: WeighInRow) {
        viewModelScope.launch {
            repository.deleteWeighIn(row.id)
            undo.value = row
        }
    }

    fun undoDelete() {
        val row = undo.value ?: return
        undo.value = null
        viewModelScope.launch { repository.restoreWeighIn(row.id) }
    }

    fun dismissUndo() {
        undo.value = null
    }
}
