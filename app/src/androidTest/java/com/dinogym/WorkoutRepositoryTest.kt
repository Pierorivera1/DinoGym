package com.dinogym

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.dinogym.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class WorkoutRepositoryTest {
    @Test fun persistsAcrossRepositoryInstancesAndPreservesInvalidFiles() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "repository-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val repository = WorkoutRepository(directory)
            assertTrue(repository.load().sessions.isEmpty())
            val data = WorkoutData(sessions = listOf(Session("Pull", listOf(Entry(starterExercises.first(), 13, 2)))))
            repository.save(data)
            assertEquals(data, WorkoutRepository(directory).load())
            val archived = data.archiveExercise(starterExercises.first().id)
            repository.save(archived)
            val reloaded = WorkoutRepository(directory).load()
            assertEquals(archived, reloaded)
            assertTrue(reloaded.exercises.single { it.id == starterExercises.first().id }.archived)
            assertEquals(data.history(starterExercises.first().id), reloaded.history(starterExercises.first().id))
            val file = File(directory, "workouts.json")
            file.writeText("invalid content")
            try {
                repository.load()
                fail("A damaged file must not silently become an empty workout history")
            } catch (_: kotlinx.serialization.SerializationException) {
                assertEquals("invalid content", file.readText())
            }
        } finally { directory.deleteRecursively() }
    }
}
