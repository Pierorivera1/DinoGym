package com.dinogym

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dinogym.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ExerciseRepositoryTest {
    @Test fun legacyFileLoadsActiveExercisesWithoutRewritingTheFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "legacy-exercises-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = File(directory, "workouts.json")
            val legacy = """{"exercises":[{"id":"pull","name":"Pull-ups","routine":"Pull"}],"sessions":[{"id":"old-session","routine":"Pull","performedAt":100,"entries":[{"exercise":{"id":"pull","name":"Pull-ups","routine":"Pull"},"value":9,"sets":3}]}]}"""
            file.writeText(legacy)
            val loaded = WorkoutRepository(directory).load()
            assertFalse(loaded.exercises.single().archived)
            assertFalse(loaded.sessions.single().entries.single().exercise.archived)
            assertEquals("9 reps × 3", loaded.sessions.single().entries.single().display)
            assertEquals(legacy, file.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun exerciseLifecyclePersistsWithoutChangingHistoricalEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "exercise-lifecycle-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val exercise = starterExercises.first()
            val session = Session("Pull", listOf(Entry(exercise, 9, 3, "Controlled")))
            val data = WorkoutData(listOf(exercise), listOf(session))
            val edited = data.updateExercise(exercise.copy(name = "Chin-ups"))
                .archiveExercise(exercise.id)
            WorkoutRepository(directory).save(edited)
            val archived = WorkoutRepository(directory).load()
            assertEquals(edited, archived)
            assertEquals(listOf(session), archived.sessions)
            WorkoutRepository(directory).save(archived.restoreExercise(exercise.id))
            val restored = WorkoutRepository(directory).load()
            assertFalse(restored.exercises.single().archived)
            assertEquals("Chin-ups", restored.exercises.single().name)
            assertEquals(listOf(session), restored.sessions)
        } finally { directory.deleteRecursively() }
    }
}
