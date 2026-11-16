package io.github.pierorivera1.dinogym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.pierorivera1.dinogym.ui.DinoGymNavigation

/** The only activity. Every screen is a composable inside [DinoGymNavigation]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DinoGymNavigation()
        }
    }
}
