package io.github.pierorivera1.dinogym.ui.programs

import io.github.pierorivera1.dinogym.data.repository.CycleStepInfo
import io.github.pierorivera1.dinogym.data.repository.ProgramWithCycle
import io.github.pierorivera1.dinogym.data.repository.RoutineSummary
import io.github.pierorivera1.dinogym.domain.model.CycleStep
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.Program
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Programs screens' state mapping and editing rules (targets, picker search). */
class ProgramsRulesTest {

    private fun program(id: String, active: Boolean, currentStep: Int, vararg routines: String?) = ProgramWithCycle(
        Program(id, id.uppercase(), ProgramType.CALISTHENICS, currentStep, active),
        routines.mapIndexed { i, r -> CycleStepInfo(CycleStep("$id-$i", id, i, r?.lowercase()), r) },
    )

    private fun exercise(id: String, name: String, icon: ExerciseIcon = ExerciseIcon.PULL_UP, kind: ExerciseKind = ExerciseKind.REPS) =
        Exercise(id, name, icon, kind, template = null, weightEnabled = false, archived = false)

    // Programs list

    @Test
    fun onlyTheActiveProgramMarksItsNextStep() {
        val state = ProgramListMapper.map(
            listOf(program("mine", true, 1, "Pull", "Push", null), program("short", false, 1, "Upper", "Lower")),
            emptyList(),
        )
        val mine = state.programs[0]
        assertEquals(listOf(false, true, false), mine.cycle.map { it.isCurrent })
        assertEquals("Push", mine.nextRoutine)
        assertTrue(state.programs[1].cycle.none { it.isCurrent })
    }

    @Test
    fun aRestDayNextIsFlagged() {
        val card = ProgramListMapper.map(listOf(program("mine", true, 2, "Pull", "Push", null)), emptyList()).programs[0]
        assertNull(card.nextRoutine)
        assertTrue(card.nextIsRest)
        assertEquals(3, card.stepCount)
    }

    @Test
    fun anEmptyProgramHasNoNextStep() {
        val card = ProgramListMapper.map(listOf(program("new", true, 0)), emptyList()).programs[0]
        assertNull(card.nextRoutine)
        assertFalse(card.nextIsRest)
    }

    // Program editor

    @Test
    fun theEditorShowsExerciseCountsAndTheNextStep() {
        val state = ProgramEditMapper.map(
            program("mine", false, 1, "Pull", null),
            nameDraft = null,
            routines = listOf(RoutineSummary(Routine("pull", "Pull"), exerciseCount = 6, setCount = 14)),
            sheet = null,
            undo = null,
        )
        assertEquals("MINE", state.name)
        assertEquals(6, state.steps[0].exerciseCount)
        assertEquals(listOf(false, true), state.steps.map { it.isNext })
        assertTrue(state.steps[1].routineId == null)
    }

    @Test
    fun theTypedNameWinsAndBlankIsAnError() {
        val state = ProgramEditMapper.map(program("mine", true, 0), "  ", emptyList(), null, null)
        assertEquals("  ", state.name)
        assertTrue(state.nameError)
    }

    @Test
    fun aDeletedProgramIsMissing() {
        assertTrue(ProgramEditMapper.map(null, null, emptyList(), null, null).isMissing)
    }

    // Targets

    private val fixed = Target(sets = 2, repMin = 6, repMax = 6, restSeconds = 150)

    @Test
    fun setsStayBetweenOneAndTheMaximum() {
        assertEquals(3, RoutineEditRules.withSets(fixed, 1).sets)
        assertEquals(1, RoutineEditRules.withSets(fixed.copy(sets = 1), -1).sets)
        assertEquals(RoutineEditRules.MAX_SETS, RoutineEditRules.withSets(fixed.copy(sets = RoutineEditRules.MAX_SETS), 1).sets)
    }

    @Test
    fun aFixedTargetMovesBothEnds() {
        val t = RoutineEditRules.withRepMin(fixed, 1)
        assertEquals(7, t.repMin)
        assertEquals(7, t.repMax)
        assertEquals(1, RoutineEditRules.withRepMin(fixed.copy(repMin = 1, repMax = 1), -1).repMin)
    }

    @Test
    fun rangeOpensTwoAboveAndFixedKeepsTheBottom() {
        val range = RoutineEditRules.withRange(fixed, true)
        assertEquals(6, range.repMin)
        assertEquals(8, range.repMax)
        assertTrue(range.isRange)
        val back = RoutineEditRules.withRange(range, false)
        assertEquals(6, back.repMin)
        assertEquals(6, back.repMax)
    }

    @Test
    fun aRangeNeverCollapses() {
        val range = Target(sets = 3, repMin = 6, repMax = 7, restSeconds = 90)
        assertEquals(6, RoutineEditRules.withRepMin(range, 1).repMin)
        assertEquals(7, RoutineEditRules.withRepMax(range, -1).repMax)
        assertEquals(5, RoutineEditRules.withRepMin(range, -1).repMin)
        assertEquals(8, RoutineEditRules.withRepMax(range, 1).repMax)
    }

    @Test
    fun defaultTargetsFollowTheTrainingRest() {
        assertEquals(150, RoutineEditRules.defaultTarget(exercise("p", "Pull-ups")).restSeconds)
        assertEquals(90, RoutineEditRules.defaultTarget(exercise("c", "Curl", ExerciseIcon.CURL)).restSeconds)
        val rope = RoutineEditRules.defaultTarget(exercise("r", "Jump rope", ExerciseIcon.JUMP_ROPE, ExerciseKind.SHOW_ONLY))
        assertEquals(Target(1, 0, 0, 0), rope)
    }

    // Picker

    private val library = listOf(
        exercise("biceps", "Curl de bíceps", ExerciseIcon.CURL),
        exercise("dom", "Dominadas"),
        exercise("neutral", "Dominadas agarre neutro"),
        exercise("dips", "Fondos", ExerciseIcon.DIP),
    )

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf("biceps"), PickerRules.state(library, emptySet(), "BICEPS", emptyList()).rows.map { it.id })
        assertEquals(listOf("dom", "neutral"), PickerRules.state(library, emptySet(), " domin", emptyList()).rows.map { it.id })
        assertEquals(4, PickerRules.state(library, emptySet(), "", emptyList()).rows.size)
    }

    @Test
    fun tickedRowsStayVisibleAndInRoutineIsTagged() {
        val state = PickerRules.state(library, inRoutine = setOf("dom"), query = "fondos", selected = listOf("neutral"))
        assertEquals(listOf("neutral", "dips"), state.rows.map { it.id })
        assertTrue(state.rows[0].checked)
        assertEquals(1, state.selectedCount)
        assertTrue(PickerRules.state(library, setOf("dom"), "", emptyList()).rows.first { it.id == "dom" }.inRoutine)
    }

    @Test
    fun onlyActiveTickedExercisesAreAddedInTickOrder() {
        // "gone" was archived after it was ticked: it is no longer in the active library.
        val toAdd = PickerRules.toAdd(library, listOf("dips", "gone", "dom"))
        assertEquals(listOf("dips", "dom"), toAdd.map { it.id })
        assertEquals(2, PickerRules.state(library, emptySet(), "", listOf("dips", "gone", "dom")).selectedCount)
    }

    @Test
    fun aNewExerciseIsNamedAfterTheSearch() {
        assertEquals("Muscle-up", PickerRules.newExerciseName("  Muscle-up ", "New exercise"))
        assertEquals("New exercise", PickerRules.newExerciseName(" ", "New exercise"))
    }
}
