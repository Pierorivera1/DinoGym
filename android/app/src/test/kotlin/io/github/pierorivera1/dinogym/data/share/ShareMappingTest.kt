package io.github.pierorivera1.dinogym.data.share

import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.models.ShareSnapshot
import io.github.pierorivera1.dinogym.api.models.SharedCycleStep
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ShareMappingTest {
    private val muscleUp = ExerciseTemplate("Muscle-up", ExerciseIcon.LEVER, ExerciseKind.TEMPLATE, "3s + {n} pulls + 3s")
    private val curl = ExerciseTemplate("Curl", ExerciseIcon.CURL, ExerciseKind.REPS, weightEnabled = true)
    private val rope = ExerciseTemplate("Jump rope", ExerciseIcon.JUMP_ROPE, ExerciseKind.SHOW_ONLY)

    private val pull = RoutineTemplate(
        "PULL",
        listOf(
            RoutineItemTemplate(muscleUp, 2, 3, 5, 150),
            RoutineItemTemplate(curl, 3, 10, 10, 90),
            RoutineItemTemplate(rope, 1, 0, 0, 0),
        ),
    )
    private val legs = RoutineTemplate("LEGS", listOf(RoutineItemTemplate(rope, 1, 0, 0, 0)))
    private val program = ProgramTemplate("Mine", ProgramType.CALISTHENICS, listOf(pull, legs), listOf(0, 1, null, 0))

    @Test
    fun aRoutineRoundTripsThroughJson() {
        val snapshot = ShareMapping.toSnapshot(pull)
        assertEquals(ShareSnapshot.Kind.ROUTINE, snapshot.kind)
        assertNull(snapshot.program)
        assertEquals(SharedContent.Routine(pull), ShareMapping.fromSnapshot(reencode(snapshot)))
    }

    @Test
    fun aProgramKeepsItsCycleAndRestDays() {
        val snapshot = ShareMapping.toSnapshot(program)
        assertEquals(listOf(0, 1, null, 0), snapshot.program!!.cycle.map { it.routine })
        assertEquals(SharedContent.Program(program), ShareMapping.fromSnapshot(reencode(snapshot)))
    }

    @Test
    fun aRestDayIsLeftOutOnTheWire() {
        // The contract has no null routine: a rest day is a step without one (decision 96).
        val json = DinoGymApi.JSON.encodeToString(ShareSnapshot.serializer(), ShareMapping.toSnapshot(program))
        assertFalse(json.contains("null"))
        assertEquals(true, json.contains("{}"))
    }

    @Test
    fun iconKeysAreTheAppsEnumNames() {
        assertEquals("LEVER", ShareMapping.toSnapshot(pull).routines[0].exercises[0].exercise.iconKey)
    }

    @Test
    fun namesAreTrimmedAndCut() {
        val long = RoutineTemplate("  " + "x".repeat(130) + "  ", listOf(RoutineItemTemplate(curl.copy(name = " "), 1, 1, 1, 0)))
        val shared = ShareMapping.toSnapshot(long).routines[0]
        assertEquals(120, shared.name.length)
        assertEquals("?", shared.exercises[0].exercise.name)
    }

    @Test
    fun checksWhatCannotBeShared() {
        assertEquals(ShareMapping.Problem.EMPTY, ShareMapping.check(program.copy(cycle = listOf(null, null))))
        val big = RoutineTemplate("BIG", List(31) { RoutineItemTemplate(curl, 1, 1, 1, 0) })
        assertEquals(ShareMapping.Problem.TOO_BIG, ShareMapping.check(big))
        assertEquals(ShareMapping.Problem.TOO_BIG, ShareMapping.check(program.copy(routines = listOf(big, legs))))
        assertNull(ShareMapping.check(program))
        assertNull(ShareMapping.check(pull))
    }

    @Test
    fun unreadableSnapshotsAreNull() {
        val snapshot = ShareMapping.toSnapshot(program)
        assertNull(ShareMapping.fromSnapshot(snapshot.copy(version = 2)))
        assertNull(ShareMapping.fromSnapshot(snapshot.copy(program = null)))
        val badCycle = snapshot.program!!.copy(cycle = listOf(SharedCycleStep(routine = 5)))
        assertNull(ShareMapping.fromSnapshot(snapshot.copy(program = badCycle)))
        assertNull(ShareMapping.fromSnapshot(ShareMapping.toSnapshot(pull).copy(routines = emptyList())))
    }

    @Test
    fun anUnknownIconFallsBackToGeneric() {
        val snapshot = ShareMapping.toSnapshot(pull)
        val line = snapshot.routines[0].exercises[0]
        val odd = snapshot.copy(
            routines = listOf(snapshot.routines[0].copy(exercises = listOf(line.copy(exercise = line.exercise.copy(iconKey = "KETTLEBELL"))))),
        )
        val routine = (ShareMapping.fromSnapshot(odd) as SharedContent.Routine).routine
        assertEquals(ExerciseIcon.GENERIC, routine.items[0].exercise.icon)
    }

    private fun reencode(snapshot: ShareSnapshot): ShareSnapshot =
        DinoGymApi.JSON.decodeFromString(ShareSnapshot.serializer(), DinoGymApi.JSON.encodeToString(ShareSnapshot.serializer(), snapshot))
}
