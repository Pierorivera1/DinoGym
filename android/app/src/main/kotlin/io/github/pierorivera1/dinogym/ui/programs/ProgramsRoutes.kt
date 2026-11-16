package io.github.pierorivera1.dinogym.ui.programs

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The Programs list itself is the Programs tab, ProgramsRoute in ui/navigation/Routes.kt.
// Both editors are pushed screens: not tabs, so the bottom bar is hidden on them.

/**
 * The program editor for [programId]. [isNew] is true right after `+ NEW` created it: the
 * name field opens focused with the default name selected, so typing replaces it.
 */
@Serializable
data class ProgramEditRoute(val programId: String, val isNew: Boolean = false) : NavKey

/**
 * The routine editor for [routineId], opened from the Programs list, a program's cycle, or
 * the edit button on Home's Up next card. [isNew] focuses the name, as for programs.
 */
@Serializable
data class RoutineEditRoute(val routineId: String, val isNew: Boolean = false) : NavKey
