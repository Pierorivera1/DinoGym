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

// The workout flow. None of these is a tab, so the bottom bar is hidden on them (tabFor is null).

/** The active exercise of the workout in progress. The session is the one open in Room. */
@Serializable
data object WorkoutRoute : NavKey

/** The rest timer, pushed on top of [WorkoutRoute]. Back returns to the exercise; the rest keeps running. */
@Serializable
data object RestRoute : NavKey

/** The summary of the finished session [sessionId]. */
@Serializable
data class SummaryRoute(val sessionId: String) : NavKey

/**
 * The screens to put on top of Home when the app starts, so a workout survives a restart:
 * the active exercise when a session is in progress, plus the rest screen when a rest is
 * still running. Nothing when no workout is open.
 */
fun resumeScreens(workoutInProgress: Boolean, restRunning: Boolean): List<NavKey> = when {
    !workoutInProgress -> emptyList()
    restRunning -> listOf(WorkoutRoute, RestRoute)
    else -> listOf(WorkoutRoute)
}

/** The route each bottom bar tab opens. */
fun BottomTab.route(): NavKey = when (this) {
    BottomTab.HOME -> HomeRoute
    BottomTab.PROGRAMS -> ProgramsRoute
    BottomTab.STATS -> StatsRoute
    BottomTab.EXERCISES -> ExercisesRoute
}

/**
 * The tab to highlight for [key], or null when [key] is not a top-level screen. Null hides
 * the bottom bar, which is how the workout flow hides it.
 */
fun tabFor(key: NavKey): BottomTab? = BottomTab.entries.firstOrNull { it.route() == key }
