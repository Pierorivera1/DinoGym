package io.github.pierorivera1.dinogym.ui.stats

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The Stats tab itself is StatsRoute in ui/navigation/Routes.kt. These are pushed screens:
// not tabs, so the bottom bar is hidden on them.

/** A past session ([sessionId]): its sets, editable, and delete. Opened from history and from the summary's EDIT. */
@Serializable
data class SessionRoute(val sessionId: String) : NavKey

/** The progress view of one exercise. Opened from the PROGRESS tab and from a past session. */
@Serializable
data class ProgressRoute(val exerciseId: String) : NavKey
