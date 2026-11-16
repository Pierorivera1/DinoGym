package io.github.pierorivera1.dinogym.ui.stats

import io.github.pierorivera1.dinogym.data.repository.SessionListItem
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the HISTORY tab shows: finished sessions grouped by month, newest first. */
data class StatsHistoryUiState(
    val isLoading: Boolean = true,
    val months: List<HistoryMonth> = emptyList(),
)

/** One month heading (`NOVEMBER 2026`) and its sessions, newest first. */
data class HistoryMonth(val month: YearMonth, val rows: List<HistoryRow>)

/** One finished session in the history list. Tapping it opens [sessionId]. */
data class HistoryRow(
    val sessionId: String,
    val date: LocalDate,
    val routineName: String,
    val durationSeconds: Long,
    val setCount: Int,
    /** Exercises whose set 1 set a new best in this session: the `▲ n BEST` tag. 0 hides it. */
    val bestCount: Int,
)

/** Builds [StatsHistoryUiState]. A plain function, so it is unit-tested. */
object StatsHistoryMapper {
    /**
     * [sessions] come newest first from the database; [newBests] is session id → exercise ids
     * that set a new best. Months are in the device's [zone], so a late-night workout counts
     * on the day the user saw it.
     */
    fun map(sessions: List<SessionListItem>, newBests: Map<String, Set<String>>, zone: ZoneId): StatsHistoryUiState {
        val rows = sessions
            .sortedByDescending { it.session.startedAt }
            .map { item ->
                val session = item.session
                HistoryRow(
                    sessionId = session.id,
                    date = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate(),
                    routineName = item.routineName,
                    durationSeconds = ((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0) / 1000,
                    setCount = item.setCount,
                    bestCount = newBests[session.id]?.size ?: 0,
                )
            }
        val months = rows
            .groupBy { YearMonth.from(it.date) }
            .map { (month, monthRows) -> HistoryMonth(month, monthRows) }
        return StatsHistoryUiState(isLoading = false, months = months)
    }
}

/** What the PROGRESS tab shows: every active exercise with its best set 1. */
data class ProgressListUiState(
    val isLoading: Boolean = true,
    val rows: List<ProgressListRow> = emptyList(),
)

/** One exercise in the PROGRESS tab. [best] is null when set 1 was never logged. */
data class ProgressListRow(
    val exerciseId: String,
    val name: String,
    val icon: ExerciseIcon,
    val best: SetOneResult?,
    /** Show [best] as `20 kg × 8` instead of `8`. */
    val usesWeight: Boolean,
)

/** Builds [ProgressListUiState]. A plain function, so it is unit-tested. */
object ProgressListMapper {
    /**
     * [exercises] are the active ones, sorted by name like the library. Show-only exercises
     * (jump rope) are never logged, so they have no progress and are left out.
     */
    fun map(exercises: List<Exercise>, bests: Map<String, SetOneResult>): ProgressListUiState = ProgressListUiState(
        isLoading = false,
        rows = exercises
            .filter { it.kind != ExerciseKind.SHOW_ONLY }
            .map { exercise ->
                val best = bests[exercise.id]
                ProgressListRow(exercise.id, exercise.name, exercise.icon, best, usesWeight = (best?.weightKg ?: 0.0) > 0.0)
            },
    )
}
