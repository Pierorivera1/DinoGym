package io.github.pierorivera1.dinogym.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.pierorivera1.dinogym.R
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
import io.github.pierorivera1.dinogym.ui.settings.LicencesEntry
import io.github.pierorivera1.dinogym.ui.settings.LicencesRoute
import io.github.pierorivera1.dinogym.ui.settings.SettingsEntry
import io.github.pierorivera1.dinogym.ui.settings.SettingsRoute
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
                        onEditRoutine = { /* The routine editor arrives with the Programs task. */ },
                        onOpenSettings = { if (backStack.last() == HomeRoute) backStack.add(SettingsRoute) },
                        onOpenHistory = { backStack.openTab(BottomTab.STATS) },
                    )
                }
                entry<ProgramsRoute> { PlaceholderScreen(R.string.nav_programs) }
                entry<StatsRoute> { PlaceholderScreen(R.string.nav_stats) }
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
                        onEditSession = { /* Editing a past session arrives with the Stats task. */ },
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

/** Stand-in for a screen that is not built yet: its title on the background. */
@Composable
private fun PlaceholderScreen(@StringRes title: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DinoTheme.colors.bg)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text(stringResource(title), style = DinoTheme.type.title, color = DinoTheme.colors.outline)
    }
}
