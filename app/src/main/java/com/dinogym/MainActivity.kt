package com.dinogym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dinogym.theme.DinoGymTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val preferences = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
      var darkTheme by remember {
        mutableStateOf(preferences.getBoolean("dark_mode", false))
      }
      DinoGymTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(darkTheme = darkTheme, onDarkThemeChange = { dark ->
            preferences.edit().putBoolean("dark_mode", dark).apply()
            darkTheme = dark
          })
        }
      }
    }
  }
}
