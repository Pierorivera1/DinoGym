package com.dinogym

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.dinogym.data.*
import com.dinogym.theme.DinoGymTheme
import com.dinogym.ui.main.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logWorkoutThenSeeItsProgress() {
        compose.setContent {
            var data by remember { mutableStateOf(WorkoutData(exercises = starterExercises.take(1))) }
            var routine by remember { mutableStateOf<String?>(null) }
            DinoGymTheme {
                val state = WorkoutState(data, loading = false)
                if (routine == null) HomeScreen(state, { routine = it }, {}, {}, { _, _ -> })
                else WorkoutScreen(routine!!, state, { routine = null }, {
                    data = data.copy(sessions = data.sessions + it)
                    routine = null
                })
            }
        }
        compose.onNodeWithText("Start workout").performClick()
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
        compose.onNodeWithText("reps").performTextInput("13")
        compose.onNodeWithText("Finish workout").assertIsEnabled().performClick()
        compose.onNodeWithText("Progress", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Pull · Latest: 13 reps × 2").assertExists()
    }

    @Test fun draftSurvivesActivityStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DinoGymTheme { WorkoutScreen("Pull", WorkoutState(WorkoutData(exercises = starterExercises.take(1)), loading = false), {}, {}) }
        }
        compose.onNodeWithText("reps").performTextInput("9")
        compose.onNodeWithText("Notes (optional)").performTextInput("Slow tempo")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("Slow tempo").assertExists()
        compose.onNodeWithText("Finish workout").assertIsEnabled()
    }
}
