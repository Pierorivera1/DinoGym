package io.github.pierorivera1.dinogym.ui.exercises

import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.rules.PatternRules

/** Everything the exercise editor shows. Built by [ExerciseEditRules.uiState]. */
data class ExerciseEditUiState(
    val isLoading: Boolean = true,
    /** The exercise does not exist (or was deleted): the screen says so. */
    val isMissing: Boolean = false,
    val name: String = "",
    val icon: ExerciseIcon = ExerciseIcon.GENERIC,
    val kind: ExerciseKind = ExerciseKind.REPS,
    /** The pattern as typed, for PATTERN. Kept while another kind is chosen, so it is not lost. */
    val pattern: String = "",
    val weightEnabled: Boolean = false,
    val archived: Boolean = false,
    /** Names of the routines that use it, for the note under ARCHIVE. */
    val usedIn: List<String> = emptyList(),
) {
    /** A blank name is shown as an error and not saved. */
    val nameError: Boolean get() = name.isBlank()

    /** A pattern without `{n}` is shown as an error and not saved. */
    val patternError: Boolean get() = kind == ExerciseKind.TEMPLATE && !PatternRules.isValid(pattern)
}

/**
 * The editor's rules as plain functions, so they are unit-tested: what the screen shows for
 * the exercise being edited, and what of it may be written to the database.
 */
object ExerciseEditRules {

    /** The number used in the pattern preview: `3s + 4 pulls + 3s`. */
    const val PREVIEW_NUMBER = 4

    fun uiState(draft: Exercise, usedIn: List<String>) = ExerciseEditUiState(
        isLoading = false,
        name = draft.name,
        icon = draft.icon,
        kind = draft.kind,
        pattern = draft.template.orEmpty(),
        weightEnabled = draft.weightEnabled,
        archived = draft.archived,
        usedIn = usedIn,
    )

    /**
     * What to write for [draft], given what is [saved] now. Invalid fields are not written:
     * a blank name keeps the saved name, and a pattern without `{n}` keeps the saved pattern.
     * Edits are stored as they happen (decision 41), so this runs on every change.
     */
    fun toStore(draft: Exercise, saved: Exercise): Exercise = draft.copy(
        name = draft.name.trim().ifEmpty { saved.name },
        template = if (PatternRules.isValid(draft.template)) draft.template else saved.template,
    )

    /**
     * Switching how the exercise is logged. Choosing PATTERN with no usable pattern yet starts
     * one with just `{n}`, so the stored pattern is never missing for a pattern exercise.
     */
    fun withKind(draft: Exercise, kind: ExerciseKind): Exercise =
        if (kind == ExerciseKind.TEMPLATE && !PatternRules.isValid(draft.template)) {
            draft.copy(kind = kind, template = PatternRules.DEFAULT)
        } else {
            draft.copy(kind = kind)
        }
}
