package io.github.pierorivera1.dinogym.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryAndProgramTest {
    private val t = TestDatabase()
    private lateinit var program: ProgramWithCycle
    private val pushId get() = program.cycle[1].step.routineId!!

    @Before
    fun setUp() = runTest {
        t.seeder.seedIfNeeded()
        program = t.programs.observeActiveProgram().first()!!
    }

    @After
    fun tearDown() = t.close()

    private suspend fun dipsId() = t.routines.observeRoutine(pushId).first()!!.items.first().exercise.id

    private suspend fun pushWorkout(dipsReps: Int): String {
        val id = t.workouts.startSession(program.program.id, pushId)
        t.workouts.logSet(id, dipsId(), dipsReps, null)
        t.workouts.logSet(id, dipsId(), dipsReps - 1, null)
        t.clock.advanceMinutes(40)
        t.workouts.finishSession(id)
        t.clock.advanceMinutes(24 * 60)
        return id
    }

    @Test
    fun setOneBestsKeepOnlyImprovements() = runTest {
        pushWorkout(12)
        pushWorkout(11)
        pushWorkout(13)
        pushWorkout(13)
        assertEquals(listOf(12, 13), t.history.observeSetOneBests(dipsId()).first().map { it.reps })
        assertEquals(4, t.history.observeSetOneHistory(dipsId()).first().size)
        assertEquals(13, t.history.observeLastSetOnes(pushId).first()[dipsId()]!!.reps)
    }

    @Test
    fun summaryComparesWithThePreviousSessionOfTheRoutine() = runTest {
        pushWorkout(12)
        val id = pushWorkout(14)

        val summary = t.history.getSummary(id)!!
        assertEquals(40 * 60_000L, summary.durationMillis)
        assertEquals(2, summary.setCount)
        val dips = summary.exercises.single()
        assertEquals(14, dips.setOne!!.reps)
        assertEquals(12, dips.previousSetOne!!.reps)
        assertTrue(dips.isNewBest)
        assertEquals(1, summary.newBestCount)
        // Two workouts moved the cycle from Pull to Legs.
        assertEquals(2, summary.nextStep!!.step.position)
    }

    @Test
    fun deletedSessionIsHiddenFromHistoryAndBests() = runTest {
        pushWorkout(12)
        val id = pushWorkout(14)
        t.history.deleteSession(id)

        assertEquals(1, t.history.observeFinishedSessions().first().size)
        assertNull(t.history.observeSessionDetail(id).first())
        assertEquals(listOf(12), t.history.observeSetOneBests(dipsId()).first().map { it.reps })
    }

    @Test
    fun sessionDetailGroupsSetsByExercise() = runTest {
        val id = pushWorkout(12)
        val detail = t.history.observeSessionDetail(id).first()!!
        assertEquals(1, detail.exercises.size)
        assertEquals(listOf(1, 2), detail.exercises[0].sets.map { it.setIndex })
        assertEquals(id, t.history.observeLastFinishedSession().first()!!.session.id)
    }

    @Test
    fun deletingARoutineRemovesItsCycleStepAndKeepsTheNextStep() = runTest {
        repeat(4) { t.programs.advanceCycle(program.program.id) } // on Upper (index 4)
        val legsId = program.cycle[2].step.routineId!!

        t.routines.deleteRoutine(legsId)

        assertFalse(t.routines.observeRoutines().first().any { it.id == legsId })
        val after = t.programs.observeActiveProgram().first()!!
        assertEquals(5, after.cycle.size)
        assertEquals((0..4).toList(), after.cycle.map { it.step.position })
        assertEquals(program.cycle[4].step.id, after.nextStep!!.step.id)
    }

    @Test
    fun reorderingTheCycleKeepsTheCurrentStep() = runTest {
        t.programs.advanceCycle(program.program.id) // on Push
        val ids = program.cycle.map { it.step.id }
        t.programs.reorderCycle(program.program.id, ids.reversed())

        val after = t.programs.observeActiveProgram().first()!!
        assertEquals(ids.reversed(), after.cycle.map { it.step.id })
        assertEquals(ids[1], after.nextStep!!.step.id)
    }

    @Test
    fun programsCanBeCreatedActivatedAndDeleted() = runTest {
        val gymId = t.programs.createProgram("Gym", ProgramType.GYM)
        val upper = t.programs.addCycleStep(gymId, program.cycle[4].step.routineId)
        t.programs.addCycleStep(gymId, null)
        t.programs.setActiveProgram(gymId)

        val active = t.programs.observeActiveProgram().first()!!
        assertEquals(gymId, active.program.id)
        assertEquals(listOf(upper), active.cycle.take(1).map { it.step.id })
        assertTrue(active.cycle[1].step.isRestDay)
        assertEquals(1, t.programs.observePrograms().first().count { it.isActive })

        t.programs.removeCycleStep(upper)
        assertEquals(listOf(0), t.programs.observeActiveProgram().first()!!.cycle.map { it.step.position })

        t.programs.deleteProgram(gymId)
        assertNull(t.programs.observeActiveProgram().first())
        assertEquals(1, t.programs.observePrograms().first().size)
    }

    @Test
    fun archivedExercisesMoveToTheArchivedList() = runTest {
        val dips = dipsId()
        t.exercises.setArchived(dips, true)
        assertFalse(t.exercises.observeExercises(archived = false).first().any { it.id == dips })
        assertTrue(t.exercises.observeExercises(archived = true).first().any { it.id == dips })
        // Routines still show an archived exercise.
        assertEquals(dips, t.routines.observeRoutine(pushId).first()!!.items.first().exercise.id)
        t.exercises.setArchived(dips, false)
        assertTrue(t.exercises.observeExercises().first().any { it.id == dips })
    }

    @Test
    fun routineExercisesCanBeAddedReorderedAndRemoved() = runTest {
        val routineId = t.routines.createRoutine("Test")
        val dips = dipsId()
        val other = t.routines.observeRoutine(pushId).first()!!.items[1].exercise.id
        val a = t.routines.addExercise(routineId, dips, 3, 8, 12, 150)
        val b = t.routines.addExercise(routineId, other, 2, 10, 10, 90)

        t.routines.reorderExercises(routineId, listOf(b, a))
        assertEquals(listOf(b, a), t.routines.observeRoutine(routineId).first()!!.items.map { it.routineExercise.id })

        t.routines.updateTarget(a, 4, 6, 10, 120)
        val edited = t.routines.observeRoutine(routineId).first()!!.items.first { it.routineExercise.id == a }
        assertEquals(4, edited.routineExercise.sets)
        assertFalse(edited.routineExercise.isFixedTarget)

        t.routines.removeExercise(b)
        val items = t.routines.observeRoutine(routineId).first()!!.items
        assertEquals(listOf(a), items.map { it.routineExercise.id })
        assertEquals(0, items.single().routineExercise.position)
    }
}
