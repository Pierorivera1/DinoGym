package io.github.pierorivera1.dinogym.data.seed

import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterProgramsTest {

    private val resolveKey: (SeedText) -> String = { it.name }

    @Test
    fun fourStartersOneForEachTypeAndLength() {
        val all = StarterPrograms.all
        assertEquals(StarterKey.entries.toSet(), all.map { it.key }.toSet())
        assertEquals(ProgramType.GYM, StarterPrograms.get(StarterKey.GYM_5).program.type)
        assertEquals(ProgramType.GYM, StarterPrograms.get(StarterKey.GYM_4).program.type)
        assertEquals(ProgramType.CALISTHENICS, StarterPrograms.get(StarterKey.CALISTHENICS_5).program.type)
        assertEquals(ProgramType.CALISTHENICS, StarterPrograms.get(StarterKey.CALISTHENICS_4).program.type)
    }

    @Test
    fun cyclesFollowTrainingMd() {
        val ulPpl = listOf(
            SeedText.ROUTINE_UPPER, SeedText.ROUTINE_LOWER, null,
            SeedText.ROUTINE_PUSH, SeedText.ROUTINE_PULL, SeedText.ROUTINE_LEGS, null,
        )
        val upperLower = listOf(
            SeedText.ROUTINE_UPPER, SeedText.ROUTINE_LOWER, null,
            SeedText.ROUTINE_UPPER, SeedText.ROUTINE_LOWER, null, null,
        )
        assertEquals(ulPpl, StarterPrograms.get(StarterKey.GYM_5).program.cycle)
        assertEquals(ulPpl, StarterPrograms.get(StarterKey.CALISTHENICS_5).program.cycle)
        assertEquals(upperLower, StarterPrograms.get(StarterKey.GYM_4).program.cycle)
        assertEquals(upperLower, StarterPrograms.get(StarterKey.CALISTHENICS_4).program.cycle)
        StarterPrograms.all.forEach { starter ->
            assertEquals(starter.daysPerWeek, starter.program.cycle.count { it != null })
        }
    }

    @Test
    fun everyLibraryExerciseIsUsedAndEveryUsedExerciseIsInTheLibrary() {
        StarterPrograms.all.forEach { starter ->
            val library = starter.program.exercises.map { it.name }
            assertEquals("${starter.key} repeats a library exercise", library.size, library.toSet().size)
            val used = starter.program.routines.flatMap { r -> r.items.map { it.exercise } }.toSet()
            assertEquals(starter.key.name, library.toSet(), used)
        }
    }

    @Test
    fun gymTargetsAreSixToTen() {
        StarterPrograms.get(StarterKey.GYM_5).program.routines.flatMap { it.items }.forEach { item ->
            assertEquals(6, item.reps)
            assertEquals(10, item.repMax)
            assertTrue(item.sets in 2..3)
        }
        val routines = StarterPrograms.get(StarterKey.GYM_5).program.routines
        assertEquals(listOf(5, 5, 4, 4, 4), routines.map { it.items.size })
    }

    @Test
    fun calisthenicsTargetsMatchTheTable() {
        val routines = StarterPrograms.get(StarterKey.CALISTHENICS_5).program.routines.associateBy { it.name }
        val dips = routines.getValue(SeedText.ROUTINE_UPPER).items.first { it.exercise == SeedText.EXERCISE_DIP }
        assertEquals(3, dips.sets)
        assertEquals(8, dips.reps)
        assertEquals(12, dips.repMax)
        val nordic = routines.getValue(SeedText.ROUTINE_LOWER).items.first { it.exercise == SeedText.EXERCISE_NORDIC_CURL_NEGATIVE }
        assertEquals(4, nordic.reps)
        assertEquals(8, nordic.repMax)
        val rope = routines.getValue(SeedText.ROUTINE_LEGS).items.last()
        assertEquals(SeedText.EXERCISE_JUMP_ROPE, rope.exercise)
        assertEquals(0, rope.restSeconds)
    }

    @Test
    fun restIsCompoundOrIsolationAndRangesAreValid() {
        StarterPrograms.all.forEach { starter ->
            val showOnly = starter.program.exercises.filter { it.kind == ExerciseKind.SHOW_ONLY }.map { it.name }.toSet()
            starter.program.routines.flatMap { it.items }.filter { it.exercise !in showOnly }.forEach { item ->
                assertTrue("${item.exercise} rest", item.restSeconds == 150 || item.restSeconds == 90)
                assertTrue("${item.exercise} range", item.reps in 1..item.repMax)
            }
        }
    }

    @Test
    fun resolvedTemplateSharesOneExerciseAcrossRoutines() {
        val template = SeedResolver.resolve(StarterPrograms.get(StarterKey.CALISTHENICS_5).program, resolveKey)
        assertEquals("STARTER_CALISTHENICS_5", template.name)
        assertEquals(listOf(0, 1, null, 2, 3, 4, null), template.cycle)
        val dipLines = template.routines.flatMap { it.items }.filter { it.exercise.name == "EXERCISE_DIP" }
        assertEquals(2, dipLines.size)
        assertEquals(12, dipLines.first().repMax)
    }

    @Test
    fun ownerSeedStillResolvesFixedTargets() {
        val template = SeedResolver.resolve(OwnerProgramSeed.program, resolveKey)
        template.routines.flatMap { it.items }.forEach { assertEquals(it.repMin, it.repMax) }
        assertEquals(listOf(0, 1, 2, null, 3, 4), template.cycle)
    }
}
