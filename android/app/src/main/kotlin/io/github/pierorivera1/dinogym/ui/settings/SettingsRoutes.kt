package io.github.pierorivera1.dinogym.ui.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Settings, pushed on top of Home from its gear. Not a tab, so the bottom bar is hidden. */
@Serializable
data object SettingsRoute : NavKey

/** The open-source licences, pushed on top of [SettingsRoute]. */
@Serializable
data object LicencesRoute : NavKey
