package io.github.pierorivera1.dinogym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.pierorivera1.dinogym.ui.DinoGymNavigation
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/** The only activity. Every screen is a composable inside [DinoGymNavigation]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Follows the system dark mode until the Settings screen can choose a ThemeMode.
            DinoTheme {
                DinoGymNavigation()
            }
        }
    }
}
