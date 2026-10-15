package com.dinogym.data

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CurrentExercisesTest {
    @Test fun presetsContainRequestedRepsAndSets() {
        val expected = mapOf(
            "Pull" to mapOf("forearm-curl" to (13 to 3), "reverse-curl" to (25 to 2), "rows" to (12 to 3), "bicep-curl" to (12 to 2)),
            "Push" to mapOf("feet-elevated-push-ups" to (7 to 3), "single-arm-lateral-raises" to (10 to 2), "lateral-raises" to (14 to 2)),
            "Upper" to mapOf("neutral-grip-pull-ups" to (7 to 3)),
        )
        expected.forEach { (workout, exercises) ->
            val draft = WorkoutDraft.start(starterWorkouts.single { it.id == workout }, WorkoutData())
            exercises.forEach { (id, target) ->
                val sets = draft.exercises.single { it.exercise.id == id }.sets
                assertEquals(target.second, sets.size)
                assertTrue(sets.all { it.value == target.first.toString() && !it.completed })
            }
        }
    }

    @Test fun maxRepsRequiresActualRepsAndSurvivesRoundTrip() {
        val data = WorkoutCodec.decode(WorkoutCodec.json.encodeToString(WorkoutData()))
        val draft = WorkoutDraft.start(data.workouts.single { it.id == "Push" }, data)
        val hollow = draft.exercises.single { it.exercise.id == "hollow-push-ups" }
        assertTrue(hollow.maxReps)
        assertEquals(2, hollow.sets.size)
        assertTrue(hollow.sets.all { it.value.isBlank() && it.record() == null })
        val completed = hollow.copy(sets = listOf(DraftSet("17", completed = true), DraftSet("12", completed = true)))
        val session = draft.copy(exercises = listOf(completed)).session()
        assertEquals(listOf(17, 12), session.entries.single().records.map { it.value })
        assertTrue(data.finishSession(session, true).workouts.single { it.id == "Push" }.exercises.single { it.exerciseId == "hollow-push-ups" }.maxReps)
    }

    @Test fun upgradesOldPlansOnceWithoutOverwritingHistoryOrCustomTargets() {
        val custom = PlannedExercise("rows", listOf(SetRecord(19)), restSeconds = 90)
        val workout = WorkoutTemplate("Pull", "My pull", listOf(custom))
        val history = Session("Pull", listOf(Entry(starterExercises.first(), 5, 2)))
        val old = WorkoutData(exercises = starterExercises.take(9), workouts = listOf(workout), sessions = listOf(history))
        val root = WorkoutCodec.json.parseToJsonElement(WorkoutCodec.json.encodeToString(old)).jsonObject
        val upgraded = WorkoutCodec.decode(JsonObject(root - "exercisePresetVersion").toString())
        assertEquals(listOf(history), upgraded.sessions)
        assertEquals(custom, upgraded.workouts.single().exercises.first())
        assertEquals("My pull", upgraded.workouts.single().name)
        assertTrue(upgraded.workouts.single().exercises.any { it.exerciseId == "forearm-curl" })
        assertEquals(upgraded, upgraded.addCurrentExercises())
        val removed = upgraded.copy(workouts = listOf(workout))
        assertEquals(removed, WorkoutCodec.decode(WorkoutCodec.json.encodeToString(removed)))
        val defaultRows = old.copy(workouts = listOf(workout.copy(exercises = listOf(PlannedExercise("rows"))))).addCurrentExercises()
        assertEquals(List(3) { SetRecord(12) }, defaultRows.workouts.single().exercises.first().targets)
    }
}
