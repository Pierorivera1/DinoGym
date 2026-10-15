package com.dinogym.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WorkoutSessionTest {
    @Test fun createCorrectAndDeleteKeepOtherSessionsAndOriginalDate() {
        val original = Session("Pull", listOf(Entry(starterExercises.first(), 9, 3)), performedAt = 100)
        val other = Session("Push", listOf(Entry(starterExercises[3], 12, 2)), performedAt = 200)
        assertEquals(original.id, UUID.fromString(original.id).toString())
        val data = WorkoutData().saveSession(original).saveSession(other)
        val corrected = original.copy(entries = listOf(Entry(starterExercises.first(), 10, 4, "Corrected")), performedAt = 999)
        val updated = data.saveSession(corrected)
        assertEquals(listOf(corrected.copy(performedAt = 100), other), updated.sessions)
        assertEquals(updated, Json.decodeFromString<WorkoutData>(Json.encodeToString(updated)))
        assertEquals(listOf(other), updated.deleteSession(original.id).sessions)
        assertEquals(updated, updated.deleteSession("missing"))
    }

    @Test fun legacyValueAndSetCountRetainVolumeAndExerciseSnapshot() {
        val oldJson = """{"exercises":[],"sessions":[{"routine":"Pull","entries":[{"exercise":{"id":"pull-ups","name":"Original name","routine":"Pull"},"value":9,"sets":3}],"performedAt":100,"id":"original-session"}]}"""
        val data = WorkoutCodec.decode(oldJson)
        val reloaded = Json.decodeFromString<WorkoutData>(Json.encodeToString(data))
        assertEquals(data, reloaded)
        assertEquals(27, reloaded.sessions.single().entries.sumOf { it.value * it.sets })
        assertEquals("Original name", reloaded.sessions.single().entries.single().exercise.name)
        assertEquals("original-session", reloaded.sessions.single().id)
        assertEquals(100L, reloaded.sessions.single().performedAt)
    }

    @Test fun sessionsRejectEmptyDuplicateAndInvalidValues() {
        val exercise = starterExercises.first()
        assertThrows(IllegalArgumentException::class.java) { Session("Pull", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Session("Pull", List(2) { Entry(exercise, 1, 1) }) }
        listOf(-1, 10000).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Entry(exercise, value, 1) }
        }
        listOf(0, 100).forEach { sets ->
            assertThrows(IllegalArgumentException::class.java) { Entry(exercise, 1, sets) }
        }
        assertThrows(IllegalArgumentException::class.java) { Entry(exercise, 1, 1, "x".repeat(501)) }
    }
}
