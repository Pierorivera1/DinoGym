package com.dinogym.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test fun templatesAndEntriesKeepTheirMeaning() {
        val exercise = Exercise("hold", "Front holds", "Pull", "3s + {n} pulls + 3s", "pulls")
        val entry = Entry(exercise, 4, 2, "Controlled")
        assertEquals("3s + 4 pulls + 3s × 2", entry.display)
        val data = WorkoutData(listOf(exercise), listOf(Session("Pull", listOf(entry))))
        assertEquals(data, Json.decodeFromString<WorkoutData>(Json.encodeToString(data)))
        val edited = data.copy(exercises = listOf(exercise.copy(template = "5s + {n} pulls + 5s")))
        assertEquals(entry, edited.history("hold").single().second)
    }

    @Test fun historyIsChronologicalAndOnlyContainsTheChosenExercise() {
        val exercise = starterExercises.first()
        val older = Session("Pull", listOf(Entry(exercise, 8, 2)), performedAt = 100)
        val newer = Session("Pull", listOf(Entry(exercise, 12, 3)), performedAt = 200)
        val unrelated = Session("Push", listOf(Entry(starterExercises[3], 13, 2)), performedAt = 300)
        assertEquals(listOf(8, 12), WorkoutData(sessions = listOf(newer, unrelated, older)).history(exercise.id).map { it.second.value })
        assertTrue(WorkoutData().history(exercise.id).isEmpty())
    }

    @Test fun invalidInputCannotBecomeSavedData() {
        listOf("reps", "{n} plus {n}").forEach { template ->
            assertThrows(IllegalArgumentException::class.java) { Exercise("id", "Name", "Pull", template) }
        }
        assertThrows(IllegalArgumentException::class.java) { Exercise("id", " ", "Pull") }
        assertThrows(IllegalArgumentException::class.java) { Entry(starterExercises.first(), -1, 2) }
        assertThrows(IllegalArgumentException::class.java) { Entry(starterExercises.first(), 13, 0) }
        assertThrows(IllegalArgumentException::class.java) { Session("Pull", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            Session("Pull", List(2) { Entry(starterExercises.first(), 13, 2) })
        }
    }
}
