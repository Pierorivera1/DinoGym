package io.github.pierorivera1.dinogym.data.seed

import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerProgramSeedTest {

    private val seed = OwnerProgramSeed.program

    @Test
    fun isACalisthenicsProgramWithFiveRoutines() {
        assertEquals(ProgramType.CALISTHENICS, seed.type)
        assertEquals(
            listOf(
                SeedText.ROUTINE_PULL,
                SeedText.ROUTINE_PUSH,
                SeedText.ROUTINE_LEGS,
                SeedText.ROUTINE_UPPER,
                SeedText.ROUTINE_LOWER,
            ),
            seed.routines.map { it.name },
        )
    }

    @Test
    fun cycleIsPullPushLegsRestUpperLower() {
        assertEquals(6, seed.cycle.size)
        assertEquals(
            listOf(
                SeedText.ROUTINE_PULL,
                SeedText.ROUTINE_PUSH,
                SeedText.ROUTINE_LEGS,
                null,
                SeedText.ROUTINE_UPPER,
                SeedText.ROUTINE_LOWER,
            ),
            seed.cycle,
        )
        val routineNames = seed.routines.map { it.name }.toSet()
        assertTrue(seed.cycle.filterNotNull().all { it in routineNames })
    }

    @Test
    fun sharedExercisesAreOneLibraryEntry() {
        val names = seed.exercises.map { it.name }
        assertEquals("duplicate exercise in the library", names.size, names.toSet().size)
        // Every routine item points at a library exercise.
        val used = seed.routines.flatMap { r -> r.items.map { it.exercise } }.toSet()
        assertEquals(names.toSet(), used)
        // Dips are used by Push and Upper.
        val dipRoutines = seed.routines.filter { r -> r.items.any { it.exercise == SeedText.EXERCISE_DIP } }
        assertEquals(listOf(SeedText.ROUTINE_PUSH, SeedText.ROUTINE_UPPER), dipRoutines.map { it.name })
    }

    @Test
    fun noRoutineListsTheSameExerciseTwice() {
        seed.routines.forEach { routine ->
            val exercises = routine.items.map { it.exercise }
            assertEquals("${routine.name} repeats an exercise", exercises.size, exercises.toSet().size)
        }
    }

    @Test
    fun lowerHasTheSameExercisesAsLegs() {
        val legs = seed.routines.first { it.name == SeedText.ROUTINE_LEGS }
        val lower = seed.routines.first { it.name == SeedText.ROUTINE_LOWER }
        assertEquals(legs.items, lower.items)
    }

    @Test
    fun targetsMatchTheWishlistTable() {
        val push = seed.routines.first { it.name == SeedText.ROUTINE_PUSH }.items
        val dips = push.first { it.exercise == SeedText.EXERCISE_DIP }
        assertEquals(3, dips.sets)
        assertEquals(12, dips.reps)
        val upperDips = seed.routines.first { it.name == SeedText.ROUTINE_UPPER }.items
            .first { it.exercise == SeedText.EXERCISE_DIP }
        assertEquals(2, upperDips.sets)
        assertEquals(11, upperDips.reps)
        assertEquals(6, seed.routines.first { it.name == SeedText.ROUTINE_PULL }.items.size)
        assertEquals(7, seed.routines.first { it.name == SeedText.ROUTINE_UPPER }.items.size)
    }

    @Test
    fun frontLeverIsATemplateAndJumpRopeIsShowOnly() {
        val lever = seed.exercises.first { it.name == SeedText.EXERCISE_FRONT_LEVER }
        assertEquals(ExerciseKind.TEMPLATE, lever.kind)
        assertEquals(SeedText.TEMPLATE_FRONT_LEVER, lever.template)
        val rope = seed.exercises.first { it.name == SeedText.EXERCISE_JUMP_ROPE }
        assertEquals(ExerciseKind.SHOW_ONLY, rope.kind)
        assertNull(rope.template)
        seed.exercises.filter { it.kind != ExerciseKind.TEMPLATE }.forEach { assertNull(it.template) }
    }

    @Test
    fun restIsCompoundOrIsolationExceptShowOnly() {
        val showOnly = seed.exercises.filter { it.kind == ExerciseKind.SHOW_ONLY }.map { it.name }.toSet()
        seed.routines.flatMap { it.items }.forEach { item ->
            if (item.exercise in showOnly) {
                assertEquals(0, item.restSeconds)
            } else {
                assertTrue("${item.exercise} rest ${item.restSeconds}", item.restSeconds == 150 || item.restSeconds == 90)
                assertTrue(item.sets > 0 && item.reps > 0)
            }
        }
    }
}
