package com.dinogym

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.dinogym.data.*
import com.dinogym.theme.DinoGymTheme
import com.dinogym.ui.main.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrainingPlannerUiTest {
    @Test fun longPressDragReordersExercisesAndSavesOrder() {
        val workout = starterWorkouts.first().copy(exercises = starterWorkouts.first().exercises.take(3))
        var saved: WorkoutTemplate? = null
        compose.setContent {
            DinoGymTheme {
                PlannerScreen(WorkoutState(WorkoutData(workouts = listOf(workout)), loading = false), {},
                    { value, _ -> saved = value }, { _, _ -> }, {}, initialWorkoutId = workout.id)
            }
        }
        compose.onNodeWithText("Front holds + pulls").performClick()
        val first = compose.onNodeWithContentDescription("Reorder Pull-ups", useUnmergedTree = true)
        val second = compose.onNodeWithContentDescription("Reorder Front holds + pulls", useUnmergedTree = true)
        val distance = second.fetchSemanticsNode().boundsInRoot.center.y - first.fetchSemanticsNode().boundsInRoot.center.y
        first.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, distance + 25f), 400)
            up()
        }
        compose.onNodeWithText("Move up").assertDoesNotExist()
        compose.onNodeWithText("Save workout").performClick()
        compose.runOnIdle {
            assertEquals(listOf("front-pulls", "pull-ups", "rows"), saved!!.exercises.map { it.exerciseId })
        }
    }

    @Test fun deleteWorkoutRequiresConfirmationAndRemovesPlan() {
        var data by mutableStateOf(WorkoutData())
        compose.setContent {
            DinoGymTheme {
                PlannerScreen(WorkoutState(data, loading = false), {}, { _, _ -> }, { _, _ -> }, {},
                    initialWorkoutId = "Pull", onDeleteWorkout = { id, done -> data = data.deleteWorkout(id); done() })
            }
        }
        compose.onNodeWithText("Delete workout").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(data.workouts.any { it.id == "Pull" }) }
        compose.onNodeWithText("Delete workout").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.runOnIdle { assertFalse(data.workouts.any { it.id == "Pull" }) }
    }

    @get:Rule val compose = createComposeRule()

    @Test fun frontHoldFormatTracksEditedRepetitions() {
        var sets by mutableStateOf(listOf(DraftSet()))
        compose.setContent {
            DinoGymTheme {
                SetRows(sets, starterExercises.first { it.id == "front-pulls" }, true, { sets = it })
            }
        }
        compose.onNodeWithText("3s + 8 pulls + 3s").assertExists()
        compose.onNodeWithText("pulls").performTextReplacement("12")
        compose.onNodeWithText("3s + 12 pulls + 3s").assertExists()
        compose.onNodeWithText("kg (optional)").assertDoesNotExist()
        compose.onNodeWithText("Add weight").performClick()
        compose.onNodeWithText("kg (optional)").assertExists()
        compose.onNodeWithText("Hide weight").performClick()
        compose.runOnIdle {
            assertEquals(SetRecord(12), sets.single().record())
        }
    }

    @Test fun createWorkoutFromSharedLibraryThenFollowCustomSplit() {
        var data by mutableStateOf(WorkoutData())
        compose.setContent {
            DinoGymTheme {
                PlannerScreen(WorkoutState(data, loading = false), {},
                    { workout, done -> data = data.saveWorkout(workout); done() },
                    { split, done -> data = data.saveSplit(split); done() },
                    { data = data.selectSplit(it) })
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("New workout"))
        compose.onNodeWithText("New workout").performClick()
        compose.onNodeWithText("Workout name").performTextInput("Upper custom")
        closeSoftKeyboard()
        compose.onNodeWithText("Add exercise").performClick()
        compose.onNodeWithText("Search exercises").performTextInput("Dips")
        closeSoftKeyboard()
        compose.onNode(hasText("Dips") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onAllNodesWithText("reps").onFirst().performTextReplacement("12")
        closeSoftKeyboard()
        compose.onNodeWithText("Save workout").performClick()
        compose.runOnIdle {
            val workout = data.workouts.last()
            assertEquals("Upper custom", workout.name)
            assertEquals("dips", workout.exercises.single().exerciseId)
            assertEquals(12, workout.exercises.single().targets.first().value)
            assertEquals(starterExercises, data.exercises)
            assertEquals(8, data.workouts.first { it.name == "Push" }.exercises.first().targets.first().value)
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("New split"))
        compose.onNodeWithText("New split").performClick()
        compose.onNodeWithText("Split name").performTextInput("My upper rotation")
        closeSoftKeyboard()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add Upper custom"))
        compose.onNodeWithText("Add Upper custom").performClick()
        compose.onNodeWithText("Save split").performClick()
        compose.onNodeWithText("Follow split").performClick()
        compose.runOnIdle { assertEquals("Upper custom", data.nextWorkout!!.name) }
    }
}
