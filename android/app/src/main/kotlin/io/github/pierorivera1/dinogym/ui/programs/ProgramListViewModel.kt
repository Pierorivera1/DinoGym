package io.github.pierorivera1.dinogym.ui.programs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Programs list: every program with its cycle and every routine with its size. `+ NEW`
 * creates a program or routine with a default name and hands its id to the screen, which
 * opens its editor (decision 41).
 */
@HiltViewModel
class ProgramListViewModel @Inject constructor(
    private val programRepository: ProgramRepository,
    private val routineRepository: RoutineRepository,
    private val app: Application,
) : ViewModel() {

    /** True while `+ NEW` is writing, so a double tap does not create two rows. */
    private var creating = false

    val state: StateFlow<ProgramListUiState> = combine(
        programRepository.observeProgramsWithCycles(),
        routineRepository.observeRoutineSummaries(),
        ProgramListMapper::map,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramListUiState())

    /**
     * `+ NEW` program: Calisthenics, like the owner's program. It becomes the active program
     * only when none is active, so Home is never left without one by choice.
     */
    fun createProgram(onCreated: (programId: String) -> Unit) = create(onCreated) {
        programRepository.createProgram(app.getString(R.string.programs_new_name), ProgramType.CALISTHENICS, activateIfNone = true)
    }

    fun createRoutine(onCreated: (routineId: String) -> Unit) = create(onCreated) {
        routineRepository.createRoutine(app.getString(R.string.routines_new_name))
    }

    private fun create(onCreated: (String) -> Unit, write: suspend () -> String) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                onCreated(write())
            } finally {
                creating = false
            }
        }
    }
}
