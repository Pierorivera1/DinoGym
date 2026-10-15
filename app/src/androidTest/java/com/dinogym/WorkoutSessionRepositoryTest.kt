package com.dinogym

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dinogym.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class WorkoutSessionRepositoryTest {
    @Test fun sessionUpdatesAndDeletesRoundTripWithoutChangingOtherWorkouts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "session-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val repository = WorkoutRepository(directory)
            val first = Session("Pull", listOf(Entry(starterExercises.first(), 9, 3)), performedAt = 100)
            val other = Session("Push", listOf(Entry(starterExercises[3], 12, 2)), performedAt = 200)
            repository.save(WorkoutData().saveSession(first).saveSession(other))
            val corrected = first.copy(entries = listOf(Entry(first.entries.single().exercise, 10, 4, "Corrected")), performedAt = 999)
            repository.save(repository.load().saveSession(corrected))
            val updated = WorkoutRepository(directory).load()
            assertEquals(listOf(corrected.copy(performedAt = first.performedAt), other), updated.sessions)
            repository.save(updated.deleteSession(first.id))
            assertEquals(listOf(other), WorkoutRepository(directory).load().sessions)
        } finally { directory.deleteRecursively() }
    }
}
