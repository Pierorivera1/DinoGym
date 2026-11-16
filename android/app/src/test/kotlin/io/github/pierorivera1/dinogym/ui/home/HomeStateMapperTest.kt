package io.github.pierorivera1.dinogym.ui.home

import io.github.pierorivera1.dinogym.data.repository.CycleStepInfo
import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.data.repository.RoutineDetail
import io.github.pierorivera1.dinogym.data.repository.RoutineItem
import io.github.pierorivera1.dinogym.domain.model.CycleStep
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.Program
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.Routine
import io.github.pierorivera1.dinogym.domain.model.RoutineExercise
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HomeStateMapperTest {

    private val today = LocalDate.of(2026, 11, 16)

    private fun program(currentStep: Int) = ProgramWithCycle(
        program = Program("p", "My program", ProgramType.CALISTHENICS, currentStep, isActive = true),
        cycle = listOf("pull" to "Pull", null to null, "upper" to "Upper").mapIndexed { i, (id, name) ->
            CycleStepInfo(CycleStep("s$i", "p", i, id), name)
        },
    )

    private fun exercise(id: String, kind: ExerciseKind) = Exercise(id, id, ExerciseIcon.GENERIC, kind, null, false, false)

    private val pull = RoutineDetail(
        routine = Routine("pull", "Pull"),
        items = listOf(
            RoutineItem(RoutineExercise("r1", "pull", "lever", 0, 2, 4, 4, 150), exercise("lever", ExerciseKind.TEMPLATE)),
            RoutineItem(RoutineExercise("r2", "pull", "pullup", 1, 2, 6, 6, 150), exercise("pullup", ExerciseKind.REPS)),
            RoutineItem(RoutineExercise("r3", "pull", "rope", 2, 1, 0, 0, 0), exercise("rope", ExerciseKind.SHOW_ONLY)),
        ),
    )

    private fun setOne(reps: Int) = SetOneResult("x", 0, reps, null)

    @Test
    fun `next routine lists exercises, counts sets without show-only, and talks about a reps exercise`() {
        val state = HomeStateMapper.map(today, ZoneOffset.UTC, program(0), null, null, pull, mapOf("lever" to setOne(4), "pullup" to setOne(6)))
        val next = state.program!!.next as HomeNext.Workout
        assertEquals(3, next.exercises.size)
        assertEquals(4, next.totalSets)
        assertEquals(LastSetOne("pullup", 6), next.lastSetOne)
        assertEquals(false, next.inProgress)
        assertEquals(listOf(true, false, false), state.program!!.cycle.map { it.isCurrent })
    }

    @Test
    fun `rest day names the routine after it`() {
        val state = HomeStateMapper.map(today, ZoneOffset.UTC, program(1), null, null, null, emptyMap())
        assertEquals(HomeNext.RestDay("Upper"), state.program!!.next)
        assertEquals(2, state.program!!.stepNumber)
    }

    @Test
    fun `a workout in progress is offered to resume`() {
        val session = Session("s", "pull", "p", startedAt = 0, endedAt = null, currentPosition = 1)
        val state = HomeStateMapper.map(today, ZoneOffset.UTC, program(0), session, null, pull, emptyMap())
        assertTrue((state.program!!.next as HomeNext.Workout).inProgress)
    }

    @Test
    fun `no active program gives the empty state`() {
        assertNull(HomeStateMapper.map(today, ZoneOffset.UTC, null, null, null, null, emptyMap()).program)
    }
}
