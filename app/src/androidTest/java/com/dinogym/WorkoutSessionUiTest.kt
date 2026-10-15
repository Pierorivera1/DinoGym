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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WorkoutSessionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restDurationUsesMinutesAndSecondsAndAppliesToWorkout() {
        val exercise = starterExercises.first()
        val workout = WorkoutTemplate("Pull", "Pull", listOf(PlannedExercise(exercise.id)))
        var draft: WorkoutDraft? = null
        compose.setContent {
            DinoGymTheme {
                WorkoutScreen("Pull", WorkoutState(WorkoutData(exercises = listOf(exercise), workouts = listOf(workout)), loading = false),
                    {}, {}, onDraft = { draft = it })
            }
        }
        compose.onNodeWithContentDescription("Rest timer").performClick()
        compose.onNodeWithText("Minutes").assertTextContains("2")
        compose.onNodeWithText("Seconds").assertTextContains("45")
        compose.onNodeWithText("Seconds").performTextReplacement("60")
        compose.onNodeWithText("Set duration").assertIsNotEnabled()
        compose.onNodeWithText("Seconds").performTextReplacement("30")
        compose.onNodeWithText("Minutes").performTextReplacement("60")
        compose.onNodeWithText("Set duration").assertIsNotEnabled()
        compose.onNodeWithText("Minutes").performTextReplacement("3")
        compose.onNodeWithText("Set duration").performClick()
        compose.runOnIdle { assertEquals(210, draft!!.exercises.single().restSeconds) }
        compose.onNodeWithText("Pull-ups").performClick()
        compose.onNodeWithText("Rest · 3:30").performClick()
        compose.onNodeWithText("Minutes").performTextReplacement("0")
        compose.onNodeWithText("Seconds").performTextReplacement("00")
        compose.onNodeWithText("Set duration").performClick()
        compose.onNodeWithText("Rest · 0:00").assertExists()
        compose.runOnIdle { assertEquals(0, draft!!.exercises.single().restSeconds) }
    }

    @Test fun newSessionValidatesRowsAndSavesOnlyLoggedSets() {
        var saved: Session? = null
        val exercise = starterExercises.first()
        val workout = WorkoutTemplate("Pull", "Pull", listOf(PlannedExercise(exercise.id, List(2) { SetRecord(8) }, 0)))
        compose.setContent {
            DinoGymTheme {
                WorkoutScreen("Pull", WorkoutState(WorkoutData(exercises = listOf(exercise), workouts = listOf(workout)), loading = false),
                    {}, { saved = it })
            }
        }
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
        compose.onNodeWithText("Pull-ups").performClick()
        compose.onAllNodesWithText("reps").onFirst().performTextReplacement("-1")
        compose.onNodeWithText("Log set").assertIsNotEnabled()
        compose.onAllNodesWithText("reps").onFirst().performTextReplacement("0")
        compose.onNodeWithText("Add weight").performClick()
        compose.onAllNodesWithText("kg (optional)").onFirst().performTextReplacement("-2")
        compose.onNodeWithText("Log set").assertIsNotEnabled()
        compose.onAllNodesWithText("kg (optional)").onFirst().performTextReplacement("2.5")
        compose.onNodeWithText("Log set").assertIsEnabled().performClick()
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("Only 1 of 2 sets are logged. Unfinished sets will not be saved.").assertExists()
        compose.onNodeWithText("Save session").performClick()
        compose.runOnIdle { assertEquals(listOf(SetRecord(0, 2.5)), saved!!.entries.single().records) }
    }

    @Test fun editArchivedEntryRestoresDraftAndKeepsIdentityAndDate() {
        val exercise = starterExercises.first()
        val session = Session("Pull", listOf(Entry(exercise, 9, 2, "Original note")), performedAt = 100)
        var saved: Session? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DinoGymTheme {
                WorkoutScreen("Pull", WorkoutState(WorkoutData(exercises = listOf(exercise.copy(archived = true)),
                    sessions = listOf(session)), loading = false), {}, { saved = it }, session)
            }
        }
        compose.onNodeWithText("Pull-ups").performClick()
        compose.onAllNodesWithText("reps").onFirst().performTextReplacement("10")
        compose.onNodeWithContentDescription("Log set 1").performClick()
        compose.onNodeWithText("Notes (optional)").performTextReplacement("Corrected")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("10").assertExists()
        compose.onNodeWithText("Corrected").assertExists()
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Save session").performClick()
        compose.runOnIdle {
            assertEquals(session.copy(entries = listOf(Entry(exercise, listOf(SetRecord(10), SetRecord(9)), "Corrected"))), saved)
        }
    }

    @Test fun historyReviewsEditsAndConfirmsDeletionOfOnlyChosenSession() {
        val first = Session("Pull", listOf(Entry(starterExercises.first(), 9, 3, "Controlled")), performedAt = 200)
        val other = Session("Push", listOf(Entry(starterExercises[3], 12, 2)), performedAt = 100)
        var data by mutableStateOf(WorkoutData(sessions = listOf(first, other)))
        var editing: String? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DinoGymTheme {
                HistoryScreen(data, {}, onEdit = { editing = it },
                    onDelete = { id, done -> data = data.deleteSession(id); done() })
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pull day"))
        compose.onNodeWithText("Pull day").performClick()
        compose.onNodeWithText("Pull-ups").assertExists()
        compose.onNodeWithText("Controlled").assertExists()
        compose.onNodeWithText("Edit workout").performClick()
        compose.runOnIdle { assertEquals(first.id, editing) }
        compose.onNodeWithText("Delete workout").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf(first, other), data.sessions) }
        compose.onNodeWithText("Delete workout").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Delete workout?").assertExists()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf(other), data.sessions) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Push day"))
        compose.onNodeWithText("Push day").assertExists()
    }

    @Test fun previousPerformanceAndNextExerciseKeepLoggingInOneFlow() {
        val exercises = starterExercises.take(2)
        val workout = WorkoutTemplate("test", "Pull", exercises.map { PlannedExercise(it.id, listOf(SetRecord(8)), 0) })
        val previous = Session("Pull", listOf(Entry(exercises.first(), 6, 1, "Controlled")), performedAt = 100)
        compose.setContent {
            DinoGymTheme {
                WorkoutScreen("test", WorkoutState(WorkoutData(workouts = listOf(workout), sessions = listOf(previous)), loading = false), {}, {})
            }
        }
        compose.onNodeWithText("Pull-ups").performClick()
        compose.onNodeWithText("6 reps × 1").assertExists()
        compose.onNodeWithText("Controlled").assertExists()
        compose.onNodeWithText("kg (optional)").assertDoesNotExist()
        compose.onNodeWithText("Log set").performClick()
        compose.onNodeWithText("Next exercise").performClick()
        compose.onNodeWithText("Front holds + pulls").assertExists()
        compose.onNodeWithText("3s + 8 pulls + 3s").assertExists()
        compose.onNodeWithText("Log set").performClick()
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("2 sets will be saved.").assertExists()
    }

    @Test fun calendarFiltersHistoryAndRestoresSelection() {
        val today = LocalDate.now()
        fun session(day: LocalDate, name: String) = Session(name, listOf(Entry(starterExercises.first(), 8, 1)),
            performedAt = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val data = WorkoutData(sessions = listOf(session(today, "Today"), session(today.minusDays(1), "Yesterday")))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { DinoGymTheme { HistoryScreen(data, {}) } }
        val label = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())) + ", 1 workouts"
        compose.onNodeWithContentDescription(label).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription(label).assertIsSelected()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Today day"))
        compose.onNodeWithText("Yesterday day").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Show all workouts"))
        compose.onNodeWithText("Show all workouts").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Yesterday day"))
        compose.onNodeWithText("Yesterday day").assertExists()
    }
}
