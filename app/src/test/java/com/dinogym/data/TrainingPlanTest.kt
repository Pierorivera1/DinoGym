package com.dinogym.data

import com.dinogym.timer.RestCountdown
import org.junit.Assert.*
import org.junit.Test

class TrainingPlanTest {
    @Test fun newRestsDefaultToTwoMinutesFortyFiveSecondsAndSavedDurationsSurvive() {
        val exercise = starterExercises.first()
        assertEquals(165, PlannedExercise(exercise.id).restSeconds)
        assertEquals(165, DraftExercise(exercise, listOf(DraftSet())).restSeconds)
        assertTrue(WorkoutDraft.start(starterWorkouts.first(), WorkoutData()).exercises.all { it.restSeconds == 165 })
        val custom = PlannedExercise(exercise.id, restSeconds = 90)
        assertEquals(custom, WorkoutCodec.json.decodeFromString<PlannedExercise>(WorkoutCodec.json.encodeToString(custom)))
        assertEquals("2:45", com.dinogym.ui.main.formatRestDuration(165))
        assertEquals("0:00", com.dinogym.ui.main.formatRestDuration(0))
        assertEquals("60:00", com.dinogym.ui.main.formatRestDuration(3600))
    }

    @Test fun sharedExerciseHasIndependentTargetsAndExplicitUpdates() {
        val dips = starterExercises.first { it.id == "dips" }
        val push = WorkoutTemplate("push", "Push", listOf(PlannedExercise(dips.id, listOf(SetRecord(8, 10.0)))))
        val upper = WorkoutTemplate("upper", "Upper", listOf(PlannedExercise(dips.id, listOf(SetRecord(12)))))
        val data = WorkoutData(exercises = listOf(dips), workouts = listOf(push, upper),
            splits = listOf(TrainingSplit("split", "Upper / Push", listOf("upper", "push"))), activeSplitId = "split")
        val session = Session("Upper", listOf(Entry(dips, listOf(SetRecord(9, 2.5)))), workoutId = "upper")
        val withoutUpdate = data.finishSession(session, false)
        assertEquals(data.workouts, withoutUpdate.workouts)
        assertEquals("push", withoutUpdate.nextWorkout!!.id)
        val updated = data.finishSession(session, true, mapOf(dips.id to 90))
        assertEquals(push, updated.workouts.first())
        assertEquals(listOf(SetRecord(9, 2.5)), updated.workouts.last().exercises.single().targets)
        assertEquals(90, updated.workouts.last().exercises.single().restSeconds)
        assertEquals(1, updated.exercises.size)
        assertEquals(updated, updated.finishSession(session, true))
        assertEquals("push", updated.deleteSession(session.id).nextWorkout!!.id)
        assertEquals(0, data.finishSession(session.copy(workoutId = "push"), false).nextWorkoutIndex)
        assertEquals("upper", updated.selectSplit("split").nextWorkout!!.id)
    }

    @Test fun legacyMigrationDeduplicatesStartersAndPreservesEveryRecordedSet() {
        val legacy = """{"exercises":[{"id":"pull-ups","name":"Pull-ups","routine":"Pull"},{"id":"upper-pull","name":"Pull-ups","routine":"Upper"}],"sessions":[{"id":"stable","performedAt":100,"routine":"Upper","entries":[{"exercise":{"id":"upper-pull","name":"Original name","routine":"Upper"},"value":9,"sets":3}]}]}"""
        val data = WorkoutCodec.decode(legacy)
        assertEquals(1, data.exercises.size)
        assertEquals("pull-ups", data.workouts.first { it.name == "Upper" }.exercises.single().exerciseId)
        val session = data.sessions.single()
        assertEquals("stable", session.id)
        assertEquals(100L, session.performedAt)
        assertEquals("Original name", session.entries.single().exercise.name)
        assertEquals(27, session.entries.single().records.sumOf { it.value })
        assertEquals("pull-ups", session.entries.single().exercise.id)
        assertEquals(data, WorkoutCodec.decode(WorkoutCodec.json.encodeToString(data)))
        val customized = WorkoutCodec.decode(legacy.replace("\"upper-pull\",\"name\":\"Pull-ups\"", "\"upper-pull\",\"name\":\"Weighted pull-ups\""))
        assertEquals(2, customized.exercises.size)
        assertThrows(IllegalArgumentException::class.java) { WorkoutCodec.decode("""{"schemaVersion":99}""") }
    }

    @Test fun legacyFileWithOmittedDefaultLibraryStillCombinesStarterHistory() {
        val legacy = """{"sessions":[{"routine":"Upper","id":"old","performedAt":100,"entries":[{"exercise":{"id":"upper-pull","name":"Pull-ups","routine":"Upper"},"value":5,"sets":2}]}]}"""
        val data = WorkoutCodec.decode(legacy)
        assertEquals(starterExercises, data.exercises)
        assertEquals(1, data.history("pull-ups").size)
        assertEquals(10, data.history("pull-ups").single().second.records.sumOf { it.value })
    }

    @Test fun draftsValidateOptionalWeightAndOnlySaveCompletedSets() {
        assertEquals(SetRecord(8), DraftSet().record())
        assertEquals(SetRecord(0, 2.5), DraftSet("0", "2.5").record())
        listOf("-1", "NaN", "Infinity", "10000", "abc").forEach { assertNull(DraftSet("8", it).record()) }
        assertNull(DraftSet("-1").record())
        val original = WorkoutDraft.start(starterWorkouts.first(), WorkoutData())
        assertThrows(IllegalArgumentException::class.java) { original.session() }
        val changed = original.copy(exercises = original.exercises.mapIndexed { i, e ->
            if (i == 0) e.copy(sets = listOf(DraftSet("7", "5", true), DraftSet("8"))) else e
        })
        assertEquals(1, changed.session().entries.size)
        assertEquals(listOf(SetRecord(7, 5.0)), changed.session().entries.single().records)
        assertEquals(changed.id, changed.session().id)
        assertEquals(changed.startedAt, changed.session().performedAt)
        assertEquals(changed, WorkoutCodec.json.decodeFromString<WorkoutDraft>(WorkoutCodec.json.encodeToString(changed)))
    }

    @Test fun countdownUsesDeadlineAndPauseDoesNotConsumeTime() {
        val timer = RestCountdown(deadline = 125000)
        assertEquals(120000L, timer.remaining(5000))
        assertEquals(0L, timer.remaining(130000))
        val paused = RestCountdown(pausedMs = timer.remaining(25000))
        assertEquals(100000L, paused.remaining(900000))
        assertFalse(RestCountdown().active)
    }
}
