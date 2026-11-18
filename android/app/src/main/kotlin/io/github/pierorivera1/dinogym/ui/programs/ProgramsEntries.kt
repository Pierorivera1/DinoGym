package io.github.pierorivera1.dinogym.ui.programs

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// The Programs screens as navigation entries: each joins its ViewModel to its stateless
// screen. Navigation itself stays in the app shell, which passes the callbacks. Edits
// waiting for the typing delay are written when the app goes to the background.

/**
 * The Programs tab. `isNew` is true for a program or routine `+ NEW` just created empty; a
 * starter opens with `isNew` false, since its name is already meaningful. System Back closes
 * the new-program sheet first.
 */
@Composable
fun ProgramListEntry(
    onOpenProgram: (programId: String, isNew: Boolean) -> Unit,
    onOpenRoutine: (routineId: String, isNew: Boolean) -> Unit,
    viewModel: ProgramListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.newProgramSheet, onBack = viewModel::closeNewProgram)
    ProgramListScreen(
        state = state,
        onNewProgram = viewModel::openNewProgram,
        newProgramActions = NewProgramActions(
            onEmpty = { viewModel.createProgram { onOpenProgram(it, true) } },
            onStarter = { key -> viewModel.installStarter(key) { onOpenProgram(it, false) } },
            onClose = viewModel::closeNewProgram,
        ),
        onNewRoutine = { viewModel.createRoutine { onOpenRoutine(it, true) } },
        onOpenProgram = { onOpenProgram(it, false) },
        onOpenRoutine = { onOpenRoutine(it, false) },
    )
}

/** The program editor. System Back closes an open sheet first, then leaves through [onBack]. */
@Composable
fun ProgramEditEntry(
    programId: String,
    isNew: Boolean,
    onBack: () -> Unit,
    onOpenRoutine: (routineId: String, isNew: Boolean) -> Unit,
    viewModel: ProgramEditViewModel = hiltViewModel<ProgramEditViewModel, ProgramEditViewModel.Factory>(
        key = programId,
        creationCallback = { factory -> factory.create(programId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    BackHandler(enabled = state.sheet != null, onBack = viewModel::closeSheet)
    ProgramEditScreen(
        state = state,
        actions = ProgramEditActions(
            onBack = onBack,
            onNameChange = viewModel::changeName,
            onTypeChange = viewModel::changeType,
            onActivate = viewModel::activate,
            onOpenRoutine = { onOpenRoutine(it, false) },
            onRemoveStep = viewModel::removeStep,
            onMoveStep = viewModel::moveStep,
            onOpenAddRoutine = viewModel::openAddRoutine,
            onAddRoutineStep = viewModel::addRoutineStep,
            onNewRoutineStep = { viewModel.createRoutineStep { onOpenRoutine(it, true) } },
            onAddRestDay = viewModel::addRestDay,
            onAskDelete = viewModel::askDelete,
            onConfirmDelete = { viewModel.delete(onBack) },
            onCloseSheet = viewModel::closeSheet,
            onUndo = viewModel::undoRemove,
            onUndoTimeout = viewModel::dismissUndo,
        ),
        focusName = isNew,
    )
}

/**
 * The routine editor. System Back closes the picker or a sheet first. [onOpenExercise]
 * opens the exercise editor for an exercise the picker just created.
 */
@Composable
fun RoutineEditEntry(
    routineId: String,
    isNew: Boolean,
    onBack: () -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    viewModel: RoutineEditViewModel = hiltViewModel<RoutineEditViewModel, RoutineEditViewModel.Factory>(
        key = routineId,
        creationCallback = { factory -> factory.create(routineId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    BackHandler(enabled = state.sheet != null || state.picker != null) {
        if (state.sheet != null) viewModel.closeSheet() else viewModel.closePicker()
    }
    RoutineEditScreen(
        state = state,
        actions = RoutineEditActions(
            onBack = onBack,
            onNameChange = viewModel::changeName,
            onToggleExpanded = viewModel::toggleExpanded,
            onSetsChange = viewModel::changeSets,
            onRangeChange = viewModel::changeRange,
            onRepMinChange = viewModel::changeRepMin,
            onRepMaxChange = viewModel::changeRepMax,
            onOpenRest = viewModel::openRest,
            onSaveRest = viewModel::saveRest,
            onRemove = viewModel::removeExercise,
            onMove = viewModel::moveExercise,
            onAskDelete = viewModel::askDelete,
            onConfirmDelete = { viewModel.delete(onBack) },
            onCloseSheet = viewModel::closeSheet,
            onUndo = viewModel::undoRemove,
            onUndoTimeout = viewModel::dismissUndo,
            picker = PickerActions(
                onOpen = viewModel::openPicker,
                onClose = viewModel::closePicker,
                onQueryChange = viewModel::changeQuery,
                onToggle = viewModel::toggleExercise,
                onNewExercise = { viewModel.createExercise(onOpenExercise) },
                onAdd = viewModel::addSelected,
            ),
        ),
        focusName = isNew,
    )
}
