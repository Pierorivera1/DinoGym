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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.BottomBar
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import io.github.pierorivera1.dinogym.ui.navigation.ExercisesRoute
import io.github.pierorivera1.dinogym.ui.navigation.HomeRoute
import io.github.pierorivera1.dinogym.ui.navigation.ProgramsRoute
import io.github.pierorivera1.dinogym.ui.navigation.StatsRoute
import io.github.pierorivera1.dinogym.ui.navigation.route
import io.github.pierorivera1.dinogym.ui.navigation.tabFor
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * The app shell: the navigation host plus the bottom bar. The back stack is a list of
 * [NavKey]s that survives rotation and process death; [NavDisplay] shows the top one.
 * The bar shows only while a top-level screen is on top (see [tabFor]).
 * Must be called inside [DinoTheme].
 */
@Composable
fun DinoGymNavigation() {
    val backStack = rememberNavBackStack(HomeRoute)
    val tab = tabFor(backStack.last())
    Column(Modifier.fillMaxSize().background(DinoTheme.colors.bg)) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.weight(1f),
            entryProvider = entryProvider {
                entry<HomeRoute> { PlaceholderScreen(R.string.nav_home) }
                entry<ProgramsRoute> { PlaceholderScreen(R.string.nav_programs) }
                entry<StatsRoute> { PlaceholderScreen(R.string.nav_stats) }
                entry<ExercisesRoute> { PlaceholderScreen(R.string.nav_exercises) }
            },
        )
        if (tab != null) {
            BottomBar(
                selected = tab,
                onTabClick = { backStack.openTab(it) },
                onStartClick = ::startNextWorkout,
            )
        }
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

/** START in the bottom bar. Starting the next routine in the cycle arrives with the workout flow. */
private fun startNextWorkout() = Unit

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
