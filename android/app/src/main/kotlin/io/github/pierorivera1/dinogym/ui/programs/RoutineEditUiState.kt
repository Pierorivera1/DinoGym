package io.github.pierorivera1.dinogym.ui.programs

import io.github.pierorivera1.dinogym.data.repository.RoutineDetail
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.rules.TextFold

/** Everything the routine editor shows. Built by [RoutineEditMapper]. */
data class RoutineEditUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    /** The name as typed; a blank name is shown as an error and not saved. */
    val name: String = "",
    /** The saved name, for the picker's "Adding to the Pull routine" and the dialogs. */
    val savedName: String = "",
    val items: List<RoutineItemUi> = emptyList(),
    /** The card open in place; one at a time. */
    val expandedId: String? = null,
    val sheet: RoutineSheet? = null,
    /** The exercise picker, while it is open. */
    val picker: PickerUiState? = null,
    /** The name of the exercise just removed, while UNDO is offered. */
    val undo: RemovedExercise? = null,
) {
    val nameError: Boolean get() = name.isBlank()

    /** Sets to log; show-only exercises add none (as on Home). */
    val totalSets: Int get() = items.filter { it.kind != ExerciseKind.SHOW_ONLY }.sumOf { it.target.sets }
}

/** One exercise of the routine with its target. [id] is the routine-exercise id. */
data class RoutineItemUi(
    val id: String,
    val exerciseId: String,
    val name: String,
    val icon: ExerciseIcon,
    val kind: ExerciseKind,
    val target: Target,
)

/** A routine exercise's target. A fixed target has [repMin] == [repMax]. */
data class Target(val sets: Int, val repMin: Int, val repMax: Int, val restSeconds: Int) {
    val isRange: Boolean get() = repMin != repMax
}

/** The sheets the routine editor draws over itself (the picker is separate, see [PickerUiState]). */
sealed interface RoutineSheet {
    data class Rest(val itemId: String, val exerciseName: String, val seconds: Int) : RoutineSheet

    /** Delete dialog. [programNames] are the programs whose cycles lose the routine. */
    data class ConfirmDelete(val programNames: List<String>) : RoutineSheet

    /** A workout of this routine is open, so it cannot be deleted now. */
    data object DeleteBlocked : RoutineSheet
}

/** What UNDO needs to put a removed exercise back. */
data class RemovedExercise(val itemId: String, val position: Int, val name: String)

/** The exercise picker: the search, the rows it shows, and what is ticked (in tick order). */
data class PickerUiState(
    val query: String = "",
    val rows: List<PickerRow> = emptyList(),
    val selectedCount: Int = 0,
)

/** One exercise in the picker. [inRoutine] shows the IN ROUTINE tag. */
data class PickerRow(
    val id: String,
    val name: String,
    val icon: ExerciseIcon,
    val kind: ExerciseKind,
    val template: String?,
    val inRoutine: Boolean,
    val checked: Boolean,
)

/** Builds [RoutineEditUiState]. A plain function, so it is unit-tested. */
object RoutineEditMapper {
    fun map(
        routine: RoutineDetail?,
        nameDraft: String?,
        targetOverrides: Map<String, Target>,
        expandedId: String?,
        sheet: RoutineSheet?,
        picker: PickerInput?,
        library: List<Exercise>,
        undo: RemovedExercise?,
    ): RoutineEditUiState {
        if (routine == null) return RoutineEditUiState(isLoading = false, isMissing = true)
        val items = routine.items.map { item ->
            val re = item.routineExercise
            RoutineItemUi(
                id = re.id,
                exerciseId = re.exerciseId,
                name = item.exercise.name,
                icon = item.exercise.icon,
                kind = item.exercise.kind,
                target = targetOverrides[re.id] ?: Target(re.sets, re.repMin, re.repMax, re.restSeconds),
            )
        }
        return RoutineEditUiState(
            isLoading = false,
            name = nameDraft ?: routine.routine.name,
            savedName = routine.routine.name,
            items = items,
            expandedId = expandedId?.takeIf { id -> items.any { it.id == id } },
            sheet = sheet,
            picker = picker?.let { PickerRules.state(library, items.map { it.exerciseId }.toSet(), it.query, it.selected) },
            undo = undo,
        )
    }
}

/** The picker's own input: the search text and the ticked exercise ids, in tick order. */
data class PickerInput(val query: String = "", val selected: List<String> = emptyList())

/**
 * Editing a target from the expanded card. Every rule keeps the target valid, so what is
 * shown is always what is stored: at least 1 set and 1 rep, and a range always has its top
 * above its bottom (a range with equal ends would be a fixed target).
 */
object RoutineEditRules {
    const val MAX_SETS = 20
    const val MAX_REPS = 999

    /** Rest after compound moves and after isolation moves (TRAINING.md defaults). */
    const val COMPOUND_REST = 150
    const val ISOLATION_REST = 90

    fun withSets(t: Target, delta: Int) = t.copy(sets = (t.sets + delta).coerceIn(1, MAX_SETS))

    /** FIXED keeps the bottom of the range; RANGE opens 2 reps above the fixed number. */
    fun withRange(t: Target, range: Boolean): Target = when {
        range == t.isRange -> t
        range -> t.copy(repMax = (t.repMin + 2).coerceAtMost(MAX_REPS), repMin = t.repMin.coerceAtMost(MAX_REPS - 2))
        else -> t.copy(repMax = t.repMin)
    }

    /** The fixed number, or the bottom of a range (which stays below the top). */
    fun withRepMin(t: Target, delta: Int): Target = if (t.isRange) {
        t.copy(repMin = (t.repMin + delta).coerceIn(1, t.repMax - 1))
    } else {
        val reps = (t.repMin + delta).coerceIn(1, MAX_REPS)
        t.copy(repMin = reps, repMax = reps)
    }

    /** The top of a range, which stays above the bottom. */
    fun withRepMax(t: Target, delta: Int) = t.copy(repMax = (t.repMax + delta).coerceIn(t.repMin + 1, MAX_REPS))

    /**
     * The target an exercise gets when the picker adds it: 2 × 10 with the TRAINING.md rest
     * (1:30 for curls and raises, 2:30 for the rest). A show-only exercise gets the seed's
     * placeholder: one set of 0 that is never logged, no rest.
     */
    fun defaultTarget(exercise: Exercise): Target = when {
        exercise.kind == ExerciseKind.SHOW_ONLY -> Target(sets = 1, repMin = 0, repMax = 0, restSeconds = 0)
        exercise.icon == ExerciseIcon.CURL || exercise.icon == ExerciseIcon.RAISE ->
            Target(sets = 2, repMin = 10, repMax = 10, restSeconds = ISOLATION_REST)
        else -> Target(sets = 2, repMin = 10, repMax = 10, restSeconds = COMPOUND_REST)
    }
}

/** The exercise picker's search and list. */
object PickerRules {

    /**
     * Folds text for searching: lower case, accents removed, so "biceps" finds "Curl de
     * bíceps" and "domin" finds "Dominadas".
     */
    fun fold(text: String): String = TextFold.fold(text)

    /**
     * The rows to show: active exercises whose name contains [query], plus any ticked
     * exercise even if it no longer matches, so a tick never disappears. [library] holds only
     * active exercises, sorted by name; archived ones never appear (decision 41).
     */
    fun state(library: List<Exercise>, inRoutine: Set<String>, query: String, selected: List<String>): PickerUiState {
        val needle = fold(query)
        val chosen = selected.toSet()
        val rows = library
            .filter { it.id in chosen || needle.isEmpty() || fold(it.name).contains(needle) }
            .map { PickerRow(it.id, it.name, it.icon, it.kind, it.template, it.id in inRoutine, it.id in chosen) }
        return PickerUiState(query = query, rows = rows, selectedCount = selected.count { id -> library.any { it.id == id } })
    }

    /** The ticked ids that can be added (still active), in tick order. */
    fun toAdd(library: List<Exercise>, selected: List<String>): List<Exercise> {
        val byId = library.associateBy { it.id }
        return selected.mapNotNull { byId[it] }
    }

    /** The name `+ NEW EXERCISE` gives: the search text, or the default name when it is blank. */
    fun newExerciseName(query: String, defaultName: String): String = query.trim().ifEmpty { defaultName }
}
