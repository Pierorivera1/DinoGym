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

    @Test fun exerciseCanBeEditedArchivedAndRestoredWithoutLosingHistory() {
        val exercise = starterExercises.first()
        val restoration = StateRestorationTester(compose)
        var data by mutableStateOf(WorkoutData(listOf(exercise),
            listOf(Session("Pull", listOf(Entry(exercise, 9, 3))))))
        restoration.setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            DinoGymTheme {
                if (selected != null) ExerciseProgressScreen(selected!!, data, { selected = null })
                else HomeScreen(WorkoutState(data, loading = false), {}, { selected = it }, {}, { _, _ -> },
                    onUpdateExercise = { updated, onSaved -> data = data.updateExercise(updated); onSaved() },
                    onArchiveExercise = { id, onSaved -> data = data.archiveExercise(id); onSaved() },
                    onRestoreExercise = { id, onSaved -> data = data.restoreExercise(id); onSaved() }, initialTab = 2)
            }
        }
        compose.onNodeWithContentDescription("Edit Pull-ups").performClick()
        compose.onNodeWithText("Edit exercise").assertExists()
        compose.onNode(hasText("Pull-ups") and hasSetTextAction()).performTextClearance()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Chin-ups")
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Chin-ups").assertExists()

        compose.onNodeWithContentDescription("Archive Chin-ups").performClick()
        compose.onNodeWithText("Archive Chin-ups?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("Chin-ups", data.exercises.single().name) }
        compose.onNodeWithContentDescription("Archive Chin-ups").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Archive Chin-ups?").assertExists()
        compose.onNodeWithText("Archive", substring = false).performClick()
        compose.onNodeWithText("No exercises").assertExists()
        compose.onNodeWithText("Archived (1)").performClick()
        compose.onNodeWithText("Chin-ups").assertExists()
        compose.onNodeWithText("Restore", substring = false).performClick()
        compose.onNodeWithText("Active").performClick()
        compose.onNodeWithText("Chin-ups").assertExists()
        compose.onNodeWithText("Progress", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Latest: 9 reps × 3").assertExists().performClick()
        compose.onNodeWithText("Chin-ups").assertExists()
        compose.onNodeWithText("Recent sessions").assertExists()
        compose.runOnIdle {
            assertEquals(9, data.history(exercise.id).single().second.value)
            assertEquals("Chin-ups", data.exercises.single().name)
        }
    }

    @Test fun archivedExerciseIsNotOfferedForNewWorkout() {
        compose.setContent {
            DinoGymTheme {
                WorkoutScreen("Pull", WorkoutState(
                    WorkoutData(exercises = listOf(starterExercises.first().copy(archived = true))), loading = false,
                ), {}, {})
            }
        }
        compose.onNodeWithText("No exercises yet").assertExists()
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
    }
}
