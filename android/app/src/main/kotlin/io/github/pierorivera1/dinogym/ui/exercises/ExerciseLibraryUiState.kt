package io.github.pierorivera1.dinogym.ui.exercises

import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon

/** Which exercises the library lists: the ACTIVE / ARCHIVED segmented control. */
enum class LibraryFilter { ACTIVE, ARCHIVED }

/** Everything the exercise library shows. Built by [ExerciseLibraryStateMapper]. */
data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.ACTIVE,
    /** Counts for the segmented control labels, `ACTIVE · 16`. */
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    /** The tiles for [filter], alphabetical. */
    val tiles: List<LibraryTile> = emptyList(),
)

/** One tile of the grid: the icon and the name. Tapping it opens the editor for [id]. */
data class LibraryTile(val id: String, val name: String, val icon: ExerciseIcon)

/** Turns the two exercise lists into [ExerciseLibraryUiState]. A plain function, so it is unit-tested. */
object ExerciseLibraryStateMapper {
    /** [active] and [archived] come from the database already sorted by name. */
    fun map(filter: LibraryFilter, active: List<Exercise>, archived: List<Exercise>) = ExerciseLibraryUiState(
        isLoading = false,
        filter = filter,
        activeCount = active.size,
        archivedCount = archived.size,
        tiles = (if (filter == LibraryFilter.ACTIVE) active else archived).map { LibraryTile(it.id, it.name, it.icon) },
    )
}
