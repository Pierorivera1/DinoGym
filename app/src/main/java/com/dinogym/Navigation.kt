package com.dinogym

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.dinogym.ui.main.*

@Composable
fun MainNavigation(viewModel: WorkoutViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Main)
    if (state.loading) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            if (state.error == null) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!)
                TextButton(onClick = viewModel::load) { Text("Try again") }
            }
        }
        return
    }
    NavDisplay(
        backStack = backStack,
        onBack = { if (!state.saving) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                HomeScreen(state, onRoutine = {
                    val draft = state.draft
                    if (draft?.editing == true) backStack.add(EditWorkout(draft.id))
                    else backStack.add(Workout(draft?.workoutId ?: it))
                },
                    onProgress = { backStack.add(ExerciseProgress(it)) },
                    onHistory = { backStack.add(History) }, onAddExercise = viewModel::addExercise,
                    onUpdateExercise = viewModel::updateExercise,
                    onArchiveExercise = viewModel::archiveExercise,
                    onRestoreExercise = viewModel::restoreExercise,
                    onPlans = { backStack.add(Plans) })
            }
            entry<Workout> { key ->
                WorkoutScreen(key.routine, state, onBack = { backStack.removeLastOrNull() },
                    onSave = { session -> viewModel.saveSession(session) { backStack.removeLastOrNull() } },
                    onDraft = viewModel::updateDraft,
                    onDiscard = { viewModel.discardDraft { backStack.removeLastOrNull() } },
                    onFinish = { session, targets -> viewModel.finishWorkout(session, targets) { backStack.removeLastOrNull() } })
            }
            entry<ExerciseProgress> { key ->
                ExerciseProgressScreen(key.exerciseId, state.data, onBack = { backStack.removeLastOrNull() },
                    saving = state.saving, onUpdateExercise = viewModel::updateExercise,
                    onArchiveExercise = viewModel::archiveExercise,
                    onRestoreExercise = viewModel::restoreExercise)
            }
            entry<EditWorkout> { key ->
                val session = state.data.sessions.find { it.id == key.sessionId }
                if (session != null) WorkoutScreen(session.routine, state,
                    onBack = { backStack.removeLastOrNull() },
                    onSave = { viewModel.saveSession(it) { backStack.removeLastOrNull() } }, session = session,
                    onDraft = viewModel::updateDraft,
                    onDiscard = { viewModel.discardDraft { backStack.removeLastOrNull() } },
                    onFinish = { completed, _ -> viewModel.finishWorkout(completed, false) { backStack.removeLastOrNull() } })
            }
            entry<Plans> {
                PlannerScreen(state, { backStack.removeLastOrNull() }, viewModel::saveWorkout, viewModel::saveSplit, viewModel::selectSplit)
            }
            entry<History> {
                HistoryScreen(state.data, onBack = { backStack.removeLastOrNull() },
                    onEdit = {
                        if (state.draft == null || state.draft?.id == it) backStack.add(EditWorkout(it))
                        else viewModel.showError("Finish or discard your active workout before editing another session.")
                    }, onDelete = viewModel::deleteSession,
                    saving = state.saving)
            }
        },
    )
    if (state.error != null) {
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Please try again") },
            text = { Text(state.error!!) }, confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            })
    }
}
