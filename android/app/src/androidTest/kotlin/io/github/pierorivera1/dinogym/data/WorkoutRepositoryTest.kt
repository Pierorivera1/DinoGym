package io.github.pierorivera1.dinogym.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.domain.model.SetValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private val t = TestDatabase()
    private lateinit var program: ProgramWithCycle

    // Cycle of the seed: 0 Pull, 1 Push, 2 Legs, 3 Rest, 4 Upper, 5 Lower.
    private val pullId get() = program.cycle[0].step.routineId!!
    private val pushId get() = program.cycle[1].step.routineId!!
    private val upperId get() = program.cycle[4].step.routineId!!

    @Before
    fun setUp() = runTest {
        t.seeder.seedIfNeeded()
        program = t.programs.observeActiveProgram().first()!!
    }

    @After
    fun tearDown() = t.close()

    private suspend fun currentStep() = t.programs.observeActiveProgram().first()!!.program.currentStep

    private suspend fun dipsId() = t.routines.observeRoutine(pushId).first()!!.items.first().exercise.id

    /** Runs a whole workout of [routineId] logging [reps] for [exerciseId], then finishes it. */
    private suspend fun workout(routineId: String, exerciseId: String, vararg reps: Int): String {
        val id = t.workouts.startSession(program.program.id, routineId)
        reps.forEach { t.workouts.logSet(id, exerciseId, it, weightKg = null) }
        t.clock.advanceMinutes(45)
        t.workouts.finishSession(id)
        t.clock.advanceMinutes(24 * 60)
        return id
    }

    @Test
    fun finishEndsTheSessionAndAdvancesTheCycle() = runTest {
        val id = t.workouts.startNextWorkout()!!
        val workout = t.workouts.observeActiveWorkout().first()!!
        assertEquals(pullId, workout.routine.id)
        assertEquals(6, workout.exercises.size)

        t.workouts.logSet(id, workout.exercises[0].exercise.id, 4, null)
        t.clock.advanceMinutes(50)
        t.workouts.finishSession(id)

        assertNull(t.workouts.observeActiveWorkout().first())
        assertEquals(1, currentStep())
        val finished = t.history.observeFinishedSessions().first()
        assertEquals(listOf(id), finished.map { it.session.id })
        assertEquals(1, finished[0].setCount)

        // Finishing again must not advance a second time.
        t.workouts.finishSession(id)
        assertEquals(1, currentStep())
    }

    @Test
    fun discardDoesNotAdvanceTheCycleAndHidesTheSession() = runTest {
        val id = t.workouts.startNextWorkout()!!
        t.workouts.logSet(id, dipsId(), 10, null)
        t.workouts.discardSession(id)

        assertEquals(0, currentStep())
        assertNull(t.workouts.observeActiveWorkout().first())
        assertTrue(t.history.observeFinishedSessions().first().isEmpty())
        assertNull(t.history.observeSessionDetail(id).first())
    }

    @Test
    fun onlyOneSessionIsInProgress() = runTest {
        val first = t.workouts.startNextWorkout()!!
        assertEquals(first, t.workouts.startNextWorkout())
        assertEquals(first, t.workouts.startSession(program.program.id, upperId))
        assertEquals(pullId, t.workouts.observeActiveWorkout().first()!!.routine.id)
    }

    @Test
    fun restDayCannotBeStartedAndSkippingWraps() = runTest {
        repeat(3) { t.programs.advanceCycle(program.program.id) }
        assertEquals(3, currentStep())
        assertNull(t.workouts.startNextWorkout())

        repeat(3) { t.programs.advanceCycle(program.program.id) }
        assertEquals(0, currentStep())
    }

    @Test
    fun currentPositionPersists() = runTest {
        val id = t.workouts.startNextWorkout()!!
        t.workouts.setCurrentPosition(id, 2)
        val workout = t.workouts.observeActiveWorkout().first()!!
        assertEquals(2, workout.session.currentPosition)
        assertEquals(workout.exercises[2], workout.currentExercise)
    }

    @Test
    fun preFillComesFromTheSameRoutine() = runTest {
        val dips = dipsId()
        workout(pushId, dips, 13, 12, 11)
        workout(upperId, dips, 9)

        t.workouts.startSession(program.program.id, pushId)
        val pushDips = t.workouts.observeActiveWorkout().first()!!.exercises.first { it.exercise.id == dips }
        assertEquals(SetValues(13, null), pushDips.preFill(1))
        assertEquals(SetValues(12, null), pushDips.preFill(2))
        // A fourth set repeats the last set of last time.
        assertEquals(SetValues(11, null), pushDips.preFill(4))
        // Bests count every routine: 13 (Push) is still the best after 9 (Upper).
        assertEquals(13, pushDips.previousBest!!.reps)
        assertTrue(pushDips.isNewBest(14, null))
        assertFalse(pushDips.isNewBest(13, null))
    }

    @Test
    fun preFillWithoutHistoryUsesTheTarget() = runTest {
        t.workouts.startSession(program.program.id, upperId)
        val first = t.workouts.observeActiveWorkout().first()!!.exercises.first()
        assertEquals(SetValues(first.routineExercise.repMin, null), first.preFill(1))
        assertNull(first.previousBest)
        assertFalse(first.isNewBest(100, null))
    }

    @Test
    fun deletingASetRenumbersTheRest() = runTest {
        val dips = dipsId()
        val id = t.workouts.startSession(program.program.id, pushId)
        val s1 = t.workouts.logSet(id, dips, 12, null)
        t.workouts.logSet(id, dips, 11, null)
        t.workouts.logSet(id, dips, 10, null)

        t.workouts.deleteSet(s1)
        val sets = t.workouts.observeActiveWorkout().first()!!.exercises.first { it.exercise.id == dips }.loggedSets
        assertEquals(listOf(1, 2), sets.map { it.setIndex })
        assertEquals(listOf(11, 10), sets.map { it.reps })

        // The next set continues after the last one.
        t.workouts.logSet(id, dips, 9, null)
        val after = t.workouts.observeActiveWorkout().first()!!.exercises.first { it.exercise.id == dips }.loggedSets
        assertEquals(3, after.last().setIndex)
    }

    @Test
    fun editAndNoteAreSaved() = runTest {
        val id = t.workouts.startSession(program.program.id, pushId)
        val setId = t.workouts.logSet(id, dipsId(), 12, null)
        t.workouts.updateSet(setId, 13, 5.0, null)
        t.workouts.setNote(setId, "felt easy")
        val set = t.workouts.observeActiveWorkout().first()!!.exercises.first().loggedSets.single()
        assertEquals(13, set.reps)
        assertEquals(5.0, set.weightKg!!, 0.0)
        assertEquals("felt easy", set.note)
    }

    @Test
    fun restSecondsChangeIsSaved() = runTest {
        t.workouts.startSession(program.program.id, pushId)
        val item = t.workouts.observeActiveWorkout().first()!!.exercises.first().routineExercise
        t.routines.setRestSeconds(item.id, 200)
        assertEquals(200, t.workouts.observeActiveWorkout().first()!!.exercises.first().routineExercise.restSeconds)
        assertNotNull(t.workouts.observeActiveWorkout().first())
    }
}
