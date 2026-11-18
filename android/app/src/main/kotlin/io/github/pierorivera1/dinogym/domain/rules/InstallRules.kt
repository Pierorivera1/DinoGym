package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate

/** How a [io.github.pierorivera1.dinogym.domain.model.ProgramTemplate] joins the user's data (decision 93). */
object InstallRules {

    /**
     * The library exercise a template exercise should reuse: an active (not archived) one
     * with the same folded name and the same kind. Reusing it keeps one history per
     * exercise, so dips from a starter program and the user's own dips are one row. Null
     * means a new exercise is created.
     */
    fun match(library: List<Exercise>, wanted: ExerciseTemplate): Exercise? {
        val name = TextFold.fold(wanted.name)
        return library.firstOrNull { !it.archived && it.kind == wanted.kind && TextFold.fold(it.name) == name }
    }

    /**
     * [wanted], or `wanted 2`, `wanted 3`… when a name in [taken] already folds to it, so a
     * second "Upper" routine stays tell-apart in the Programs list.
     */
    fun uniqueName(wanted: String, taken: Collection<String>): String {
        val folded = taken.map(TextFold::fold).toSet()
        if (TextFold.fold(wanted) !in folded) return wanted
        var n = 2
        while (TextFold.fold("$wanted $n") in folded) n++
        return "$wanted $n"
    }
}
