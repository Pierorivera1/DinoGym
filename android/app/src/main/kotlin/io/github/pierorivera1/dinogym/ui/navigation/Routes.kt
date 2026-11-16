package io.github.pierorivera1.dinogym.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.pierorivera1.dinogym.ui.components.BottomTab
import kotlinx.serialization.Serializable

// Destinations. Each is @Serializable so the back stack survives rotation and process death.

/** Home: the next routine and the cycle. */
@Serializable
data object HomeRoute : NavKey

/** Programs: list and editors. */
@Serializable
data object ProgramsRoute : NavKey

/** Stats: history and progress. */
@Serializable
data object StatsRoute : NavKey

/** Exercises: the exercise library. */
@Serializable
data object ExercisesRoute : NavKey

/** The route each bottom bar tab opens. */
fun BottomTab.route(): NavKey = when (this) {
    BottomTab.HOME -> HomeRoute
    BottomTab.PROGRAMS -> ProgramsRoute
    BottomTab.STATS -> StatsRoute
    BottomTab.EXERCISES -> ExercisesRoute
}

/**
 * The tab to highlight for [key], or null when [key] is not a top-level screen. Null hides
 * the bottom bar, which is how the workout flow (a later task) will hide it.
 */
fun tabFor(key: NavKey): BottomTab? = BottomTab.entries.firstOrNull { it.route() == key }
