package io.github.pierorivera1.dinogym.ui.programs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.ProgramInstaller
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.seed.SeedResolver
import io.github.pierorivera1.dinogym.data.seed.SeedStrings
import io.github.pierorivera1.dinogym.data.seed.StarterKey
import io.github.pierorivera1.dinogym.data.seed.StarterPrograms
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Programs list: every program with its cycle and every routine with its size. `+ NEW`
 * routine creates one with a default name and hands its id to the screen, which opens its
 * editor (decision 41). `+ NEW` program opens a sheet with the starter programs and an empty
 * program (task 22).
 */
@HiltViewModel
class ProgramListViewModel @Inject constructor(
    private val programRepository: ProgramRepository,
    private val routineRepository: RoutineRepository,
    private val installer: ProgramInstaller,
    private val seedStrings: SeedStrings,
    private val app: Application,
) : ViewModel() {

    /** True while `+ NEW` is writing, so a double tap does not create two rows. */
    private var creating = false

    private val sheetOpen = MutableStateFlow(false)

    /** Resolved once: the names follow the phone's language at the time the screen opens. */
    private val starters by lazy { ProgramListMapper.starters(StarterPrograms.all, seedStrings::get) }

    val state: StateFlow<ProgramListUiState> = combine(
        programRepository.observeProgramsWithCycles(),
        routineRepository.observeRoutineSummaries(),
        sheetOpen,
    ) { programs, routines, open ->
        ProgramListMapper.map(programs, routines).copy(newProgramSheet = open, starters = if (open) starters else emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramListUiState())

    /** `+ NEW` program opens the sheet: a starter or an empty program (decision 92). */
    fun openNewProgram() {
        sheetOpen.value = true
    }

    fun closeNewProgram() {
        sheetOpen.value = false
    }

    /**
     * Copies the starter [key] in as a new program with its own routines (decision 93). Like
     * an empty program, it becomes active only when no program is.
     */
    fun installStarter(key: StarterKey, onCreated: (programId: String) -> Unit) = create(onCreated) {
        val template = SeedResolver.resolve(StarterPrograms.get(key).program, seedStrings::get)
        installer.install(template).also { sheetOpen.value = false }
    }

    /**
     * The sheet's empty program: Calisthenics, like the owner's program. It becomes the active program
     * only when none is active, so Home is never left without one by choice.
     */
    fun createProgram(onCreated: (programId: String) -> Unit) = create(onCreated) {
        programRepository.createProgram(app.getString(R.string.programs_new_name), ProgramType.CALISTHENICS, activateIfNone = true)
            .also { sheetOpen.value = false }
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
