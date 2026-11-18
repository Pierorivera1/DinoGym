package io.github.pierorivera1.dinogym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.pierorivera1.dinogym.ui.components.BottomBar
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseEditEntry
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseEditRoute
import io.github.pierorivera1.dinogym.ui.exercises.ExerciseLibraryEntry
import io.github.pierorivera1.dinogym.ui.home.HomeEntry
import io.github.pierorivera1.dinogym.ui.navigation.ExercisesRoute
import io.github.pierorivera1.dinogym.ui.navigation.HomeRoute
import io.github.pierorivera1.dinogym.ui.navigation.ProgramsRoute
import io.github.pierorivera1.dinogym.ui.navigation.RestRoute
import io.github.pierorivera1.dinogym.ui.navigation.StatsRoute
import io.github.pierorivera1.dinogym.ui.navigation.SummaryRoute
import io.github.pierorivera1.dinogym.ui.navigation.WorkoutRoute
import io.github.pierorivera1.dinogym.ui.navigation.route
import io.github.pierorivera1.dinogym.ui.navigation.tabFor
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditEntry
import io.github.pierorivera1.dinogym.ui.programs.ProgramEditRoute
import io.github.pierorivera1.dinogym.ui.programs.ProgramListEntry
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditEntry
import io.github.pierorivera1.dinogym.ui.programs.RoutineEditRoute
import io.github.pierorivera1.dinogym.ui.settings.LicencesEntry
import io.github.pierorivera1.dinogym.ui.settings.LicencesRoute
import io.github.pierorivera1.dinogym.ui.settings.SettingsEntry
import io.github.pierorivera1.dinogym.ui.settings.SettingsRoute
import io.github.pierorivera1.dinogym.ui.share.ImportEntry
import io.github.pierorivera1.dinogym.ui.share.ImportShareRoute
import io.github.pierorivera1.dinogym.ui.share.Imported
import io.github.pierorivera1.dinogym.ui.stats.ProgressEntry
import io.github.pierorivera1.dinogym.ui.stats.ProgressRoute
import io.github.pierorivera1.dinogym.ui.stats.SessionDetailEntry
import io.github.pierorivera1.dinogym.ui.stats.SessionRoute
import io.github.pierorivera1.dinogym.ui.stats.StatsEntry
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.workout.RestEntry
import io.github.pierorivera1.dinogym.ui.workout.SummaryEntry
import io.github.pierorivera1.dinogym.ui.workout.WorkoutEntry

/**
 * The app shell: the navigation host plus the bottom bar. The back stack is a list of
 * [NavKey]s that survives rotation and process death; [NavDisplay] shows the top one, and
 * each entry gets its own ViewModels (cleared when the entry leaves the stack).
 * The bar shows only while a top-level screen is on top (see [tabFor]).
 * Must be called inside [DinoTheme].
 */
@Composable
fun DinoGymNavigation(appViewModel: AppViewModel = hiltViewModel()) {
    val backStack = rememberNavBackStack(HomeRoute)
    // Ask for the notification permission at most once per activity (on the first rest).
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }

    // A workout survives a restart: at launch, reopen it (and its rest, if one is running).
    LaunchedEffect(Unit) {
        val resume = appViewModel.screensToResume()
        if (resume.isNotEmpty() && backStack.toList() == listOf(HomeRoute)) backStack.addAll(resume)
        // Shared links open on top of whatever is showing, after the resume above (task 23).
        appViewModel.incomingShares.collect { backStack.add(ImportShareRoute(it)) }
    }

    AppShell(
        tab = tabFor(backStack.last()),
        onTabClick = { backStack.openTab(it) },
        onStartClick = {
            appViewModel.start(onStarted = { backStack.openWorkout() }, onNothingToStart = { backStack.openTab(BottomTab.HOME) })
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeEntry(
                        onWorkoutStarted = { backStack.openWorkout() },
                        onEditRoutine = { backStack.add(RoutineEditRoute(it)) },
                        onOpenSettings = { if (backStack.last() == HomeRoute) backStack.add(SettingsRoute) },
                        onOpenHistory = { backStack.openTab(BottomTab.STATS) },
                    )
                }
                entry<ProgramsRoute> {
                    ProgramListEntry(
                        onOpenProgram = { id, isNew -> backStack.add(ProgramEditRoute(id, isNew)) },
                        onOpenRoutine = { id, isNew -> backStack.add(RoutineEditRoute(id, isNew)) },
                        onOpenLink = { if (backStack.last() == ProgramsRoute) backStack.add(ImportShareRoute(null)) },
                    )
                }
                entry<ProgramEditRoute> { key ->
                    ProgramEditEntry(
                        programId = key.programId,
                        isNew = key.isNew,
                        onBack = { if (backStack.last() == key) backStack.removeLastOrNull() },
                        onOpenRoutine = { id, isNew -> backStack.add(RoutineEditRoute(id, isNew)) },
                        onOpenSettings = { if (backStack.last() == key) backStack.add(SettingsRoute) },
                    )
                }
                entry<RoutineEditRoute> { key ->
                    RoutineEditEntry(
                        routineId = key.routineId,
                        isNew = key.isNew,
                        onBack = { if (backStack.last() == key) backStack.removeLastOrNull() },
                        onOpenExercise = { backStack.add(ExerciseEditRoute(it, isNew = true)) },
                        onOpenSettings = { if (backStack.last() == key) backStack.add(SettingsRoute) },
                    )
                }
                entry<ImportShareRoute> { key ->
                    ImportEntry(
                        shareId = key.shareId,
                        onBack = { if (backStack.last() == key) backStack.removeLastOrNull() },
                        onImported = { imported ->
                            // The editor replaces the import, so Back does not offer the same import again.
                            backStack.remove(key)
                            backStack.add(
                                when (imported) {
                                    is Imported.Program -> ProgramEditRoute(imported.programId)
                                    is Imported.Routine -> RoutineEditRoute(imported.routineId)
                                },
                            )
                        },
                    )
                }
                entry<StatsRoute> {
                    StatsEntry(
                        onOpenSession = { backStack.add(SessionRoute(it)) },
                        onOpenProgress = { backStack.add(ProgressRoute(it)) },
                    )
                }
                entry<SessionRoute> { key ->
                    SessionDetailEntry(
                        sessionId = key.sessionId,
                        onBack = { if (backStack.last() == key) backStack.removeLastOrNull() },
                        onDeleted = { backStack.leaveDeletedSession(key.sessionId) },
                        onOpenProgress = { if (backStack.last() == key) backStack.add(ProgressRoute(it)) },
                    )
                }
                entry<ProgressRoute> { key ->
                    ProgressEntry(key.exerciseId, onBack = { if (backStack.last() == key) backStack.removeLastOrNull() })
                }
                entry<ExercisesRoute> {
                    ExerciseLibraryEntry(onOpenExercise = { id, isNew -> backStack.add(ExerciseEditRoute(id, isNew)) })
                }
                entry<ExerciseEditRoute> { key ->
                    ExerciseEditEntry(key.exerciseId, key.isNew, onBack = { if (backStack.last() == key) backStack.removeLastOrNull() })
                }
                entry<SettingsRoute> {
                    SettingsEntry(
                        onBack = { if (backStack.last() == SettingsRoute) backStack.removeLastOrNull() },
                        onOpenLicences = { if (backStack.last() == SettingsRoute) backStack.add(LicencesRoute) },
                    )
                }
                entry<LicencesRoute> {
                    LicencesEntry(onBack = { if (backStack.last() == LicencesRoute) backStack.removeLastOrNull() })
                }
                entry<WorkoutRoute> {
                    WorkoutEntry(
                        onOpenRest = { if (backStack.last() == WorkoutRoute) backStack.add(RestRoute) },
                        onFinished = { backStack.openSummary(it) },
                        onExit = { backStack.openTab(BottomTab.HOME) },
                    )
                }
                entry<RestRoute> {
                    RestEntry(
                        onRestOver = { if (backStack.last() == RestRoute) backStack.removeLastOrNull() },
                        onFinished = { backStack.openSummary(it) },
                        onExit = { backStack.openTab(BottomTab.HOME) },
                        askForNotifications = !askedForNotifications,
                        onAskedForNotifications = { askedForNotifications = true },
                    )
                }
                entry<SummaryRoute> { key ->
                    SummaryEntry(
                        sessionId = key.sessionId,
                        onFinish = { backStack.openTab(BottomTab.HOME) },
                        onEditSession = { if (backStack.last() == key) backStack.add(SessionRoute(it)) },
                    )
                }
            },
        )
    }
}

/**
 * The shell's layout without any state: [content] above the bottom bar, which shows only
 * when [tab] is not null. Screenshot tests use it to draw a screen with the bar.
 */
@Composable
fun AppShell(
    tab: BottomTab?,
    onTabClick: (BottomTab) -> Unit,
    onStartClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DinoTheme.colors.bg)) {
        Box(Modifier.weight(1f)) { content() }
        if (tab != null) BottomBar(selected = tab, onTabClick = onTabClick, onStartClick = onStartClick)
    }
}

/**
 * Top-level navigation: Home stays at the bottom of the stack and at most one other tab sits
 * on it. So Back from any tab goes to Home, and Back from Home leaves the app.
 */
private fun NavBackStack<NavKey>.openTab(tab: BottomTab) {
    clear()
    add(HomeRoute)
    if (tab != BottomTab.HOME) add(tab.route())
}

/** The workout opens on top of Home, so finishing or discarding it lands on Home. */
private fun NavBackStack<NavKey>.openWorkout() {
    clear()
    add(HomeRoute)
    add(WorkoutRoute)
}

/** The summary replaces the workout screens: Back from it goes Home, never into a finished workout. */
private fun NavBackStack<NavKey>.openSummary(sessionId: String) {
    clear()
    add(HomeRoute)
    add(SummaryRoute(sessionId))
}

/**
 * After a session is deleted: its screen and its summary (when it was opened from the
 * summary's EDIT) leave the stack, since both would show a session that is gone. From
 * history this lands back on Stats; from the summary, on Home.
 */
private fun NavBackStack<NavKey>.leaveDeletedSession(sessionId: String) {
    removeAll { it == SessionRoute(sessionId) || it == SummaryRoute(sessionId) }
    if (isEmpty()) add(HomeRoute)
}

