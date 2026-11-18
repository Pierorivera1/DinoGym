package io.github.pierorivera1.dinogym.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pierorivera1.dinogym.data.seed.SeedResolver
import io.github.pierorivera1.dinogym.data.seed.SeedStrings
import io.github.pierorivera1.dinogym.data.seed.StarterKey
import io.github.pierorivera1.dinogym.data.seed.StarterPrograms
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramInstallerTest {
    private val t = TestDatabase()
    private val strings = SeedStrings(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = t.close()

    private fun starter(key: StarterKey) = SeedResolver.resolve(StarterPrograms.get(key).program, strings::get)

    @Test
    fun starterOnAnEmptyDatabaseBecomesActive() = runTest {
        val id = t.installer.install(starter(StarterKey.GYM_5))

        val active = t.programs.observeActiveProgram().first()!!
        assertEquals(id, active.program.id)
        assertEquals(ProgramType.GYM, active.program.type)
        assertEquals(7, active.cycle.size)
        assertEquals(listOf(false, false, true, false, false, false, true), active.cycle.map { it.step.isRestDay })
        assertEquals(5, t.routines.observeRoutines().first().size)
        assertEquals(22, t.exercises.observeExercises(archived = false).first().size)
    }

    @Test
    fun starterAfterTheSeedReusesExercisesAndNumbersRoutines() = runTest {
        t.seeder.seedIfNeeded()
        val seeded = t.programs.observeActiveProgram().first()!!

        val id = t.installer.install(starter(StarterKey.CALISTHENICS_5))

        // The owner's program stays active.
        assertEquals(seeded.program.id, t.programs.observeActiveProgram().first()!!.program.id)
        val installed = t.programs.observeProgram(id).first()!!
        assertFalse(installed.program.isActive)

        // Five more routines; the taken names got a number.
        val routineNames = t.routines.observeRoutines().first().map { it.name }
        assertEquals(10, routineNames.size)
        assertTrue(installed.cycle.mapNotNull { it.routineName }.all { it.endsWith(" 2") })

        // Ten exercises are the owner's rows (dips, pull-ups, curls, raises, squats, jump rope…):
        // 16 seeded + 7 new (pike, nordic, hip thrust, calf raise, diamond, inverted row, lunge).
        assertEquals(23, t.exercises.observeExercises().first().size)
    }

    @Test
    fun installingTheSameStarterTwiceNumbersTheProgram() = runTest {
        t.installer.install(starter(StarterKey.GYM_4))
        t.installer.install(starter(StarterKey.GYM_4))

        val names = t.programs.observePrograms().first().map { it.name }.sorted()
        assertEquals(2, names.size)
        assertEquals(names[0] + " 2", names[1])
        // Exercises are shared between the two copies.
        assertEquals(10, t.exercises.observeExercises().first().size)
    }

    @Test
    fun aSharedRoutineJoinsTheLibraryButNoProgram() = runTest {
        t.seeder.seedIfNeeded()
        val programsBefore = t.programs.observePrograms().first().size
        val exercisesBefore = t.exercises.observeExercises().first()
        val pullUps = exercisesBefore.single { it.name == "Pull-ups" }

        // As a share from another phone: other case, another new exercise.
        val id = t.installer.installRoutine(
            RoutineTemplate(
                name = "PULL",
                items = listOf(
                    RoutineItemTemplate(ExerciseTemplate("PULL-UPS", ExerciseIcon.PULL_UP, ExerciseKind.REPS), 3, 5, 8, 150),
                    RoutineItemTemplate(ExerciseTemplate("Archer row", ExerciseIcon.ROW, ExerciseKind.REPS), 2, 6, 6, 90),
                ),
            ),
        )

        val routine = t.routines.observeRoutine(id).first()!!
        assertEquals("PULL 2", routine.routine.name)
        assertEquals(pullUps.id, routine.items[0].exercise.id)
        assertEquals(listOf(3, 5, 8, 150), routine.items[0].routineExercise.let { listOf(it.sets, it.repMin, it.repMax, it.restSeconds) })
        assertEquals(exercisesBefore.size + 1, t.exercises.observeExercises().first().size)
        assertEquals(programsBefore, t.programs.observePrograms().first().size)
    }
}
