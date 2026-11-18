package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * A routine with its size, for the Programs list ("6 exercises · 14 sets"). Show-only
 * exercises count as exercises but their sets do not, as on Home.
 */
data class RoutineSummaryRow(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val setCount: Int,
)

/** Routines and the exercises inside them. Every read skips soft-deleted rows. */
@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query(
        """
        SELECT r.id, r.name, COUNT(re.id) AS exerciseCount,
            COALESCE(SUM(CASE WHEN e.kind = 'SHOW_ONLY' THEN 0 ELSE re.sets END), 0) AS setCount
        FROM routine r
        LEFT JOIN routine_exercise re ON re.routineId = r.id AND re.deletedAt IS NULL
        LEFT JOIN exercise e ON e.id = re.exerciseId
        WHERE r.deletedAt IS NULL
        GROUP BY r.id
        ORDER BY r.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(): Flow<List<RoutineSummaryRow>>

    @Query("SELECT * FROM routine WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): RoutineEntity?

    @Query("SELECT name FROM routine WHERE deletedAt IS NULL")
    suspend fun getNames(): List<String>

    @Insert
    suspend fun insert(routine: RoutineEntity)

    @Insert
    suspend fun insertAll(routines: List<RoutineEntity>)

    @Query("UPDATE routine SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE routine SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // Routine exercises

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY position")
    fun observeItems(routineId: String): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY position")
    suspend fun getItems(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercise WHERE id = :id AND deletedAt IS NULL")
    suspend fun getItem(id: String): RoutineExerciseEntity?

    @Insert
    suspend fun insertItems(items: List<RoutineExerciseEntity>)

    @Query(
        """
        UPDATE routine_exercise
        SET sets = :sets, repMin = :repMin, repMax = :repMax, restSeconds = :restSeconds, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateTarget(id: String, sets: Int, repMin: Int, repMax: Int, restSeconds: Int, now: Long)

    @Query("UPDATE routine_exercise SET restSeconds = :restSeconds, updatedAt = :now WHERE id = :id")
    suspend fun setRestSeconds(id: String, restSeconds: Int, now: Long)

    @Query("UPDATE routine_exercise SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setItemPosition(id: String, position: Int, now: Long)

    @Query("UPDATE routine_exercise SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteItem(id: String, now: Long)

    /** UNDO of a removed exercise: the row comes back at [position]. */
    @Query("UPDATE routine_exercise SET deletedAt = NULL, position = :position, updatedAt = :now WHERE id = :id")
    suspend fun restoreItem(id: String, position: Int, now: Long)

    @Query("UPDATE routine_exercise SET deletedAt = :now, updatedAt = :now WHERE routineId = :routineId AND deletedAt IS NULL")
    suspend fun softDeleteItemsOfRoutine(routineId: String, now: Long)
}
