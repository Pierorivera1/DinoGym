package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** Routines and the exercises inside them. Every read skips soft-deleted rows. */
@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): RoutineEntity?

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

    @Query("UPDATE routine_exercise SET deletedAt = :now, updatedAt = :now WHERE routineId = :routineId AND deletedAt IS NULL")
    suspend fun softDeleteItemsOfRoutine(routineId: String, now: Long)
}
