package io.github.pierorivera1.dinogym.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunSeederTest {
    private val t = TestDatabase()

    @After
    fun tearDown() = t.close()

    @Test
    fun seedInsertsTheOwnersProgram() = runTest {
        assertTrue(t.seeder.seedIfNeeded())

        val active = t.programs.observeActiveProgram().first()
        assertNotNull(active)
        active!!
        assertEquals(0, active.program.currentStep)
        assertEquals(6, active.cycle.size)
        assertEquals((0..5).toList(), active.cycle.map { it.step.position })
        assertTrue(active.cycle[3].step.isRestDay)
        assertNull(active.cycle[3].routineName)

        assertEquals(5, t.routines.observeRoutines().first().size)
        val library = t.exercises.observeExercises(archived = false).first()
        assertEquals(16, library.size)
        assertEquals(1, library.count { it.kind == ExerciseKind.TEMPLATE })
        assertEquals(1, library.count { it.kind == ExerciseKind.SHOW_ONLY })

        // Every routine's positions are 0, 1, 2… and Upper shares dips with Push.
        val details = active.cycle.mapNotNull { it.step.routineId }.map { t.routines.observeRoutine(it).first()!! }
        details.forEach { detail ->
            assertEquals(detail.items.indices.toList(), detail.items.map { it.routineExercise.position })
        }
        val pushDips = details[1].items.first().exercise
        val upperExerciseIds = details[3].items.map { it.exercise.id }
        assertTrue(pushDips.id in upperExerciseIds)
    }

    @Test
    fun seedRunsOnlyOnce() = runTest {
        assertTrue(t.seeder.seedIfNeeded())
        assertFalse(t.seeder.seedIfNeeded())
        assertEquals(1, t.programs.observePrograms().first().size)
        assertEquals(16, t.exercises.observeExercises().first().size)
    }

    @Test
    fun seedDoesNotComeBackAfterTheUserDeletesEverything() = runTest {
        t.seeder.seedIfNeeded()
        val program = t.programs.observeActiveProgram().first()!!.program
        t.programs.deleteProgram(program.id)
        t.routines.observeRoutines().first().forEach { t.routines.deleteRoutine(it.id) }

        assertFalse(t.seeder.seedIfNeeded())
        assertTrue(t.programs.observePrograms().first().isEmpty())
        assertTrue(t.routines.observeRoutines().first().isEmpty())
        assertNull(t.programs.observeActiveProgram().first())
    }
}
