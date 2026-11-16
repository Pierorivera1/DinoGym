package io.github.pierorivera1.dinogym.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.pierorivera1.dinogym.R
import kotlinx.serialization.Serializable

/** Home screen destination. */
@Serializable
data object HomeRoute : NavKey

/**
 * The navigation host. The back stack is a list of [NavKey]s that survives
 * rotation and process death; [NavDisplay] shows the entry for the top key.
 */
@Composable
fun DinoGymNavigation() {
    val backStack = rememberNavBackStack(HomeRoute)
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.app_name))
                        }
                    }
                },
            )
        }
    }
}
