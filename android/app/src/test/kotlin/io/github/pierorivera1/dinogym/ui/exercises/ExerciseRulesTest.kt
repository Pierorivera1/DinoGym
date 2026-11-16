package io.github.pierorivera1.dinogym.ui.exercises

import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.rules.PatternRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exercise library's state mapping and the editor's rules (pattern, what gets saved). */
class ExerciseRulesTest {

    private fun exercise(
        id: String = "lever",
        name: String = "Front lever + pulls",
        kind: ExerciseKind = ExerciseKind.TEMPLATE,
        template: String? = "3s + {n} pulls + 3s",
        archived: Boolean = false,
    ) = Exercise(id, name, ExerciseIcon.LEVER, kind, template, weightEnabled = false, archived = archived)

    // Pattern rules

    @Test
    fun aPatternNeedsThePlaceholder() {
        assertTrue(PatternRules.isValid("3s + {n} pulls + 3s"))
        assertTrue(PatternRules.isValid("{n}"))
        assertFalse(PatternRules.isValid("3s + n pulls + 3s"))
        assertFalse(PatternRules.isValid(""))
        assertFalse(PatternRules.isValid(null))
    }

    @Test
    fun previewPutsTheNumberIn() {
        assertEquals("3s + 4 pulls + 3s", PatternRules.preview("3s + {n} pulls + 3s", 4))
    }

    // What is saved

    @Test
    fun validEditsAreSavedTrimmed() {
        val saved = exercise()
        val draft = saved.copy(name = "  Front lever  ", template = "5s + {n} pulls")
        val stored = ExerciseEditRules.toStore(draft, saved)
        assertEquals("Front lever", stored.name)
        assertEquals("5s + {n} pulls", stored.template)
    }

    @Test
    fun aBlankNameKeepsTheSavedName() {
        val saved = exercise()
        assertEquals("Front lever + pulls", ExerciseEditRules.toStore(saved.copy(name = "   "), saved).name)
    }

    @Test
    fun aPatternWithoutPlaceholderKeepsTheSavedPattern() {
        val saved = exercise()
        val stored = ExerciseEditRules.toStore(saved.copy(template = "3s + pulls"), saved)
        assertEquals("3s + {n} pulls + 3s", stored.template)
    }

    @Test
    fun switchingToPatternWithoutOneStartsWithThePlaceholder() {
        val reps = exercise(kind = ExerciseKind.REPS, template = null)
        val switched = ExerciseEditRules.withKind(reps, ExerciseKind.TEMPLATE)
        assertEquals(ExerciseKind.TEMPLATE, switched.kind)
        assertEquals("{n}", switched.template)
    }

    @Test
    fun switchingKindsKeepsAnExistingPattern() {
        val lever = exercise()
        val reps = ExerciseEditRules.withKind(lever, ExerciseKind.REPS)
        assertEquals("3s + {n} pulls + 3s", reps.template)
        assertEquals("3s + {n} pulls + 3s", ExerciseEditRules.withKind(reps, ExerciseKind.TEMPLATE).template)
    }

    // Editor state

    @Test
    fun editorStateFlagsInvalidFields() {
        val state = ExerciseEditRules.uiState(exercise(name = "", template = "no number"), usedIn = listOf("Pull"))
        assertTrue(state.nameError)
        assertTrue(state.patternError)
        assertEquals(listOf("Pull"), state.usedIn)
    }

    @Test
    fun aBadPatternIsNoErrorWhenNotLoggedAsPattern() {
        val state = ExerciseEditRules.uiState(exercise(kind = ExerciseKind.REPS, template = null), usedIn = emptyList())
        assertFalse(state.patternError)
        assertEquals("", state.pattern)
    }

    // Library

    @Test
    fun libraryShowsTheChosenListWithBothCounts() {
        val active = listOf(exercise(id = "a", name = "Dips"), exercise(id = "b", name = "Rows"))
        val archived = listOf(exercise(id = "c", name = "Old curl", archived = true))

        val activeState = ExerciseLibraryStateMapper.map(LibraryFilter.ACTIVE, active, archived)
        assertFalse(activeState.isLoading)
        assertEquals(2, activeState.activeCount)
        assertEquals(1, activeState.archivedCount)
        assertEquals(listOf("Dips", "Rows"), activeState.tiles.map { it.name })

        val archivedState = ExerciseLibraryStateMapper.map(LibraryFilter.ARCHIVED, active, archived)
        assertEquals(listOf(LibraryTile("c", "Old curl", ExerciseIcon.LEVER)), archivedState.tiles)
    }
}
