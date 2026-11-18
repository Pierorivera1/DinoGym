package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallRulesTest {

    private fun exercise(id: String, name: String, kind: ExerciseKind = ExerciseKind.REPS, archived: Boolean = false) =
        Exercise(id, name, ExerciseIcon.GENERIC, kind, template = null, weightEnabled = false, archived = archived)

    private fun wanted(name: String, kind: ExerciseKind = ExerciseKind.REPS) = ExerciseTemplate(name, ExerciseIcon.GENERIC, kind)

    @Test
    fun matchIgnoresCaseAccentsAndSpaces() {
        val library = listOf(exercise("a", "Curl de bíceps"))
        assertEquals("a", InstallRules.match(library, wanted("  curl de BICEPS "))?.id)
    }

    @Test
    fun matchNeedsTheSameKind() {
        val library = listOf(exercise("a", "Jump rope", ExerciseKind.REPS))
        assertNull(InstallRules.match(library, wanted("Jump rope", ExerciseKind.SHOW_ONLY)))
    }

    @Test
    fun archivedExercisesAreNotReused() {
        val library = listOf(exercise("a", "Dips", archived = true), exercise("b", "Dips"))
        assertEquals("b", InstallRules.match(library, wanted("Dips"))?.id)
        assertNull(InstallRules.match(listOf(exercise("a", "Dips", archived = true)), wanted("Dips")))
    }

    @Test
    fun uniqueNameKeepsAFreeName() {
        assertEquals("Upper", InstallRules.uniqueName("Upper", listOf("Lower", "Push")))
    }

    @Test
    fun uniqueNameNumbersATakenName() {
        assertEquals("Upper 2", InstallRules.uniqueName("Upper", listOf("upper")))
        assertEquals("Upper 3", InstallRules.uniqueName("Upper", listOf("Upper", "Upper 2")))
        assertEquals("Tren superior 2", InstallRules.uniqueName("Tren superior", listOf("TREN SUPERIOR")))
    }
}
