package io.github.pierorivera1.dinogym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.pierorivera1.dinogym.ui.AppViewModel
import io.github.pierorivera1.dinogym.ui.DinoGymNavigation
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/** The only activity. Every screen is a composable inside [DinoGymNavigation]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Draw nothing until the saved theme is read (a few milliseconds): the window
            // background shows meanwhile, so a phone set to Day never flashes a Night frame.
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            themeMode?.let { mode ->
                DinoTheme(mode) {
                    DinoGymNavigation(appViewModel)
                }
            }
        }
    }
}
