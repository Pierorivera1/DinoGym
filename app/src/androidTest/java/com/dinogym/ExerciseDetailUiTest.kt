package com.dinogym

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dinogym.data.*
import com.dinogym.theme.DinoGymTheme
import com.dinogym.ui.main.ExerciseProgressScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExerciseDetailUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun workoutProgressExcludesOtherWorkouts() {
        val exercise = starterExercises.first()
        val data = WorkoutData(sessions = listOf(
            Session("Pull", listOf(Entry(exercise, 10, 2)), workoutId = "Pull"),
            Session("Upper", listOf(Entry(exercise, 15, 2)), workoutId = "Upper")))
        compose.setContent {
            DinoGymTheme { ExerciseProgressScreen(exercise.id, data, {}, workoutId = "Pull") }
        }
        compose.onNodeWithText("Weekly progress").assertExists()
        compose.onNodeWithContentDescription("Weekly best set in reps:", substring = true)
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
                listOf("Weekly best set in reps: ${weeklyProgress(data.history(exercise.id).filter { it.first.workoutId == "Pull" }).single().week}: 10")))
    }

    @Test fun detailValidatesEditsAndSupportsArchiveAndRestore() {
        val exercise = starterExercises.first()
        val session = Session("Pull", listOf(Entry(exercise, 9, 3)))
        var data by mutableStateOf(WorkoutData(listOf(exercise), listOf(session)))
        compose.setContent {
            DinoGymTheme {
                ExerciseProgressScreen(exercise.id, data, {},
                    onUpdateExercise = { updated, saved -> data = data.updateExercise(updated); saved() },
                    onArchiveExercise = { id, saved -> data = data.archiveExercise(id); saved() },
                    onRestoreExercise = { id, saved -> data = data.restoreExercise(id); saved() })
            }
        }
        compose.onNodeWithText("Edit exercise").performClick()
        compose.onNode(hasText("Pull-ups") and hasSetTextAction()).performTextReplacement("Chin-ups")
        compose.onNode(hasText("{n} reps") and hasSetTextAction()).performTextReplacement("reps")
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNode(hasText("reps") and hasSetTextAction() and hasText("Display template"))
            .performTextReplacement("{n} reps")
        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()
        compose.onNodeWithText("Chin-ups").assertExists()
        compose.onNodeWithText("Archive exercise").performClick()
        compose.onNodeWithText("Archive", substring = false).performClick()
        compose.onNodeWithText("Restore exercise").assertExists().performClick()
        compose.onNodeWithText("Archive exercise").assertExists()
        compose.runOnIdle {
            assertFalse(data.exercises.single().archived)
            assertEquals("Chin-ups", data.exercises.single().name)
            assertEquals(listOf(session), data.sessions)
        }
    }
}
