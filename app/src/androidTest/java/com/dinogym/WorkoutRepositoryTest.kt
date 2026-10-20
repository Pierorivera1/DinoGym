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
    @Test fun oldRestDurationsLoadWithinNineMinutesWithoutLosingData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "rest-migration-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val workout = WorkoutTemplate("long", "Long", listOf(PlannedExercise("pull-ups", restSeconds = 3600)))
            val data = WorkoutData(workouts = listOf(workout))
            val draft = WorkoutDraft.start(workout, data)
            val repository = WorkoutRepository(directory)
            repository.save(data)
            repository.saveDraft(draft)
            val loaded = repository.load()
            assertEquals(540, loaded.workouts.single().exercises.single().restSeconds)
            assertEquals(data.exercises, loaded.exercises)
            val loadedDraft = repository.loadDraft()!!
            assertEquals(draft.id, loadedDraft.id)
            assertEquals(draft.exercises.single().sets, loadedDraft.exercises.single().sets)
            assertEquals(540, loadedDraft.exercises.single().restSeconds)
        } finally { directory.deleteRecursively() }
    }

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
