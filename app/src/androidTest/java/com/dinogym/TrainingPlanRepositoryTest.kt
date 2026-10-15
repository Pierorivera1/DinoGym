package com.dinogym

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dinogym.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TrainingPlanRepositoryTest {
    @Test fun failedAtomicWriteLeavesWorkoutsAndDraftIntact() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "failed-write-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val repository = WorkoutRepository(directory)
            val original = WorkoutData()
            val draft = WorkoutDraft.start(original.workouts.first(), original)
            repository.save(original)
            repository.saveDraft(draft)
            // An unwritable parent prevents AtomicFile from creating its replacement file.
            assertTrue(directory.setWritable(false, false))
            try {
                repository.save(original.copy(nextWorkoutIndex = 1))
                fail("An unwritable directory must report the failed save")
            } catch (_: java.io.IOException) {
                assertEquals(original, repository.load())
                assertEquals(draft, repository.loadDraft())
            } finally { directory.setWritable(true, true) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun migrationBackupAndDraftRoundTrip() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "plan-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val legacy = """{"exercises":[{"id":"dips","name":"Dips","routine":"Push"}],"sessions":[]}"""
            val file = File(directory, "workouts.json").apply { writeText(legacy) }
            val repository = WorkoutRepository(directory)
            val data = repository.load()
            assertEquals(legacy, file.readText())
            assertFalse(File(directory, "workouts-v1.backup.json").exists())
            repository.save(data)
            assertEquals(legacy, File(directory, "workouts-v1.backup.json").readText())
            assertEquals(data, WorkoutRepository(directory).load())
            val draft = WorkoutDraft.start(data.workouts.first { it.name == "Push" }, data)
            repository.saveDraft(draft)
            assertEquals(draft, WorkoutRepository(directory).loadDraft())
            repository.saveDraft(null)
            assertNull(repository.loadDraft())
            assertEquals(data, repository.load())
        } finally { directory.deleteRecursively() }
    }
}
