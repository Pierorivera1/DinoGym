package io.github.pierorivera1.dinogym.ui.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pierorivera1.dinogym.timer.RestCountdown

/**
 * The rest timer as a navigation entry: connects [RestViewModel] to [RestScreen].
 *
 * - When the rest ends or is skipped, [onRestOver] takes the user back to the exercise.
 *   System Back does the same without cancelling the rest (the shell pops the entry).
 * - The first time a rest starts on Android 13+ without notification permission, it asks
 *   once ([askForNotifications] / [onAskedForNotifications]). Denying is fine: the timer
 *   and its alarm still work, there is just no countdown notification.
 */
@Composable
fun RestEntry(
    onRestOver: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
    onExit: () -> Unit,
    askForNotifications: Boolean,
    onAskedForNotifications: () -> Unit,
    viewModel: RestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restOver by rememberUpdatedState(onRestOver)
    val finished by rememberUpdatedState(onFinished)
    val exit by rememberUpdatedState(onExit)

    LaunchedEffect(state.isOver) {
        if (state.isOver) {
            viewModel.onRestOver()
            restOver()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.Finished -> finished(event.sessionId)
                WorkoutEvent.Exit -> exit()
                WorkoutEvent.OpenRest -> Unit
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationsGranted()
    }
    LaunchedEffect(Unit) {
        if (askForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.notificationsAllowed()) {
            onAskedForNotifications()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Only the end sheet takes Back here; otherwise Back pops to the exercise.
    BackHandler(enabled = state.showEndSheet, onBack = viewModel::closeEnd)

    RestScreen(
        state = state,
        actions = RestActions(
            onEnd = viewModel::requestEnd,
            onMinus = { viewModel.addSeconds(-RestCountdown.STEP_SECONDS) },
            onPlus = { viewModel.addSeconds(RestCountdown.STEP_SECONDS) },
            onSkip = viewModel::skip,
            onSaveAndFinish = viewModel::saveAndFinish,
            onDiscard = viewModel::discard,
            onCloseEnd = viewModel::closeEnd,
        ),
    )
}
