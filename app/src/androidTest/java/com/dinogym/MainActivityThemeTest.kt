package com.dinogym

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityThemeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = compose.activity.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private var savedPreference: Boolean? = null

    @Before fun savePreference() {
        savedPreference = if (preferences.contains("dark_mode")) preferences.getBoolean("dark_mode", false) else null
    }

    @After fun restorePreference() {
        preferences.edit().apply {
            savedPreference?.let { putBoolean("dark_mode", it) } ?: remove("dark_mode")
        }.commit()
    }

    @Test fun freshInstallDefaultsToLight() {
        preferences.edit().remove("dark_mode").commit()
        openSettingsAfterRecreation()
        compose.onNodeWithText("Dark mode").assertIsOff()
    }

    @Test fun savedLightPreferenceIsPreserved() {
        preferences.edit().putBoolean("dark_mode", false).commit()
        openSettingsAfterRecreation()
        compose.onNodeWithText("Dark mode").assertIsOff()
    }

    @Test fun savedDarkPreferenceIsPreserved() {
        preferences.edit().putBoolean("dark_mode", true).commit()
        openSettingsAfterRecreation()
        compose.onNodeWithText("Dark mode").assertIsOn()
    }

    private fun openSettingsAfterRecreation() {
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Train").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Exercises").performClick()
        compose.onNodeWithText("Settings").performClick()
    }
}
