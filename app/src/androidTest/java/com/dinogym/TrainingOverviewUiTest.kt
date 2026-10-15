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
import androidx.test.espresso.Espresso.closeSoftKeyboard

class TrainingOverviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val upper = WorkoutTemplate("upper", "Upper strength", listOf(PlannedExercise("pull-ups", listOf(SetRecord(8), SetRecord(10, 2.5)))))
    private val lower = WorkoutTemplate("lower", "Lower strength", listOf(PlannedExercise("squats")))
    private val data = WorkoutData(workouts = listOf(upper, lower),
        splits = listOf(TrainingSplit("test", "My plan", listOf("lower", "upper", "lower"))),
        activeSplitId = "test", nextWorkoutIndex = 1)

    @Test fun secondTapOpensSelectedDayAndSwipesKeepSelection() {
        compose.setContent {
            DinoGymTheme {
                HomeScreen(WorkoutState(data, loading = false), {}, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("Day 3").performClick().assertIsSelected()
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onNodeWithText("Day 3").performClick()
        compose.onNodeWithText("Day Details").assertIsSelected()
        compose.onNodeWithText("Squats").assertIsDisplayed()
        compose.onNodeWithContentDescription("Training pages").performTouchInput { swipeRight() }
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onNodeWithText("Day 3").assertIsSelected()
        compose.onNodeWithContentDescription("Training pages").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Day Details").assertIsSelected()
        compose.onNodeWithText("Squats").assertIsDisplayed()
    }

    @Test fun selectingDayShowsTargetsAndRestoresWithoutStartingWorkout() {
        var started: String? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DinoGymTheme(darkTheme = true) {
                HomeScreen(WorkoutState(data, loading = false), { started = it }, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("Day 2").assertIsSelected()
        compose.onNodeWithText("Day 3").performClick()
        compose.onNodeWithText("Day Details").performClick()
        compose.onNodeWithText("Squats").assertExists()
        compose.onNodeWithText("3 sets · 8 reps").assertExists()
        compose.runOnIdle { assertNull(started) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Day Details").assertIsSelected()
        compose.onNodeWithText("Squats").assertExists()
        compose.onAllNodesWithText("Start workout").onLast().performClick()
        compose.runOnIdle { assertEquals("lower", started) }
    }

    @Test fun dayMenuOpensDetailsAndEditsTheChosenWorkout() {
        var edited: String? = null
        compose.setContent {
            DinoGymTheme {
                HomeScreen(WorkoutState(data, loading = false), {}, {}, {}, { _, _ -> }, onEditWorkout = { edited = it })
            }
        }
        compose.onNodeWithContentDescription("Day 2 options").performClick()
        compose.onNodeWithText("View day details").performClick()
        compose.onNodeWithText("Pull-ups").assertExists()
        compose.onNodeWithText("2 sets · 8 reps / 10 reps · 2.5 kg").assertExists()
        compose.onNodeWithContentDescription("Edit workout").performClick()
        compose.runOnIdle { assertEquals("upper", edited) }
    }

    @Test fun selectingAnotherDayKeepsExistingDraftAsTheResumeAction() {
        var started: String? = null
        val draft = WorkoutDraft.start(upper, data)
        compose.setContent {
            DinoGymTheme {
                HomeScreen(WorkoutState(data, loading = false, draft = draft), { started = it }, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("Day 3").performClick()
        compose.onNodeWithText("IN PROGRESS").assertExists()
        compose.onNodeWithContentDescription("Next workout action").performClick()
        compose.runOnIdle { assertEquals("upper", started) }
    }

    @Test fun emptyPlanCanOpenWorkoutCreation() {
        var opened = false
        compose.setContent {
            DinoGymTheme {
                HomeScreen(WorkoutState(WorkoutData(workouts = emptyList(), splits = emptyList(), activeSplitId = null), loading = false),
                    {}, {}, {}, { _, _ -> }, onPlans = { opened = true })
            }
        }
        compose.onNodeWithText("No workout days yet").assertExists()
        compose.onNodeWithContentDescription("Next workout action").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun upNextStartsTheNextWorkoutEvenWhenAnotherDayIsSelected() {
        var started: String? = null
        compose.setContent {
            DinoGymTheme {
                HomeScreen(WorkoutState(data, loading = false), { started = it }, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("Day 3").performClick()
        compose.onNodeWithText("UP NEXT").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next workout action").performClick()
        compose.runOnIdle { assertEquals("upper", started) }
    }

    @Test fun directWorkoutEditingPreservesTargetsAndReturnsAfterSaving() {
        var saved: WorkoutTemplate? = null
        var closed = false
        compose.setContent {
            DinoGymTheme {
                PlannerScreen(WorkoutState(data, loading = false), { closed = true },
                    { workout, done -> saved = workout; done() }, { _, _ -> }, {}, initialWorkoutId = upper.id)
            }
        }
        compose.onNodeWithText("Workout name").performTextReplacement("Upper revised")
        closeSoftKeyboard()
        compose.onNodeWithText("Save workout").performClick()
        compose.runOnIdle {
            assertEquals(upper.copy(name = "Upper revised"), saved)
            assertTrue(closed)
        }
    }
}
