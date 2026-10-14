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
                HomeScreen(state, onRoutine = { backStack.add(Workout(it)) },
                    onProgress = { backStack.add(ExerciseProgress(it)) },
                    onHistory = { backStack.add(History) }, onAddExercise = viewModel::addExercise)
            }
            entry<Workout> { key ->
                WorkoutScreen(key.routine, state, onBack = { backStack.removeLastOrNull() },
                    onSave = { session -> viewModel.saveSession(session) { backStack.removeLastOrNull() } })
            }
            entry<ExerciseProgress> { key ->
                ExerciseProgressScreen(key.exerciseId, state.data, onBack = { backStack.removeLastOrNull() })
            }
            entry<History> { HistoryScreen(state.data, onBack = { backStack.removeLastOrNull() }) }
        },
    )
    if (state.error != null) {
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Please try again") },
            text = { Text(state.error!!) }, confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            })
    }
}
