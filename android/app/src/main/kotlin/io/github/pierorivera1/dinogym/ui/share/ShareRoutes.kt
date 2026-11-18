package io.github.pierorivera1.dinogym.ui.share

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The import screen for shared link [shareId] (task 23), pushed on top of whatever is open.
 * Null asks for a pasted link first (FROM A LINK in the new-program sheet).
 */
@Serializable
data class ImportShareRoute(val shareId: String?) : NavKey
