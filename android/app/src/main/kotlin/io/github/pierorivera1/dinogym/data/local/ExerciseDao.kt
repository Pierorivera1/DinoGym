package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** The exercise library. Archived exercises are still readable; soft-deleted ones are not. */
@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise WHERE archived = :archived AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeByArchived(archived: Boolean): Flow<List<ExerciseEntity>>

    /** Active and archived: routines and history still show archived exercises. */
    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercise WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getByIds(ids: List<String>): List<ExerciseEntity>

    /** Active and archived, for matching a template's exercises against the library. */
    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL")
    suspend fun getAll(): List<ExerciseEntity>

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query(
        """
        UPDATE exercise
        SET name = :name, iconKey = :iconKey, kind = :kind, template = :template,
            weightEnabled = :weightEnabled, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun update(
        id: String,
        name: String,
        iconKey: String,
        kind: String,
        template: String?,
        weightEnabled: Boolean,
        now: Long,
    )

    /** Names of the routines that use the exercise, once each, sorted; for the editor's archive note. */
    @Query(
        """
        SELECT DISTINCT routine.name FROM routine
        JOIN routine_exercise ON routine_exercise.routineId = routine.id
        WHERE routine_exercise.exerciseId = :exerciseId
            AND routine_exercise.deletedAt IS NULL AND routine.deletedAt IS NULL
        ORDER BY routine.name COLLATE NOCASE
        """,
    )
    fun observeRoutineNamesUsing(exerciseId: String): Flow<List<String>>

    @Query("UPDATE exercise SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)
}
