package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** A cycle step with its routine's name (null for a rest day), for Home and the cycle editor. */
data class CycleStepRow(
    val id: String,
    val programId: String,
    val position: Int,
    val routineId: String?,
    val routineName: String?,
)

/** Programs and their cycle steps. Every read skips soft-deleted rows. */
@Dao
interface ProgramDao {

    @Query("SELECT * FROM program WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    suspend fun getActive(): ProgramEntity?

    @Query("SELECT * FROM program WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): ProgramEntity?

    @Query("SELECT name FROM program WHERE deletedAt IS NULL")
    suspend fun getNames(): List<String>

    @Insert
    suspend fun insert(program: ProgramEntity)

    @Query("UPDATE program SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE program SET type = :type, updatedAt = :now WHERE id = :id")
    suspend fun setType(id: String, type: String, now: Long)

    @Query("UPDATE program SET deletedAt = :now, isActive = 0, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE program SET isActive = 0, updatedAt = :now WHERE isActive = 1")
    suspend fun clearActive(now: Long)

    @Query("UPDATE program SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun setActive(id: String, now: Long)

    @Query("UPDATE program SET currentStep = :step, updatedAt = :now WHERE id = :id")
    suspend fun setCurrentStep(id: String, step: Int, now: Long)

    // Cycle steps

    @Query(
        """
        SELECT c.id, c.programId, c.position, c.routineId, r.name AS routineName
        FROM cycle_step c LEFT JOIN routine r ON r.id = c.routineId
        WHERE c.programId = :programId AND c.deletedAt IS NULL
        ORDER BY c.position
        """,
    )
    fun observeCycle(programId: String): Flow<List<CycleStepRow>>

    /** Every step of every program, for the Programs list (soft-deleted programs have no live steps). */
    @Query(
        """
        SELECT c.id, c.programId, c.position, c.routineId, r.name AS routineName
        FROM cycle_step c LEFT JOIN routine r ON r.id = c.routineId
        WHERE c.deletedAt IS NULL
        ORDER BY c.programId, c.position
        """,
    )
    fun observeAllCycles(): Flow<List<CycleStepRow>>

    /** Names of the programs whose cycle uses the routine, once each, sorted; for the delete dialog. */
    @Query(
        """
        SELECT DISTINCT p.name FROM program p
        JOIN cycle_step c ON c.programId = p.id
        WHERE c.routineId = :routineId AND c.deletedAt IS NULL AND p.deletedAt IS NULL
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observeProgramNamesUsingRoutine(routineId: String): Flow<List<String>>

    @Query("SELECT * FROM cycle_step WHERE programId = :programId AND deletedAt IS NULL ORDER BY position")
    suspend fun getCycle(programId: String): List<CycleStepEntity>

    @Query("SELECT * FROM cycle_step WHERE id = :id AND deletedAt IS NULL")
    suspend fun getStep(id: String): CycleStepEntity?

    @Query("SELECT * FROM cycle_step WHERE routineId = :routineId AND deletedAt IS NULL")
    suspend fun getStepsUsingRoutine(routineId: String): List<CycleStepEntity>

    @Insert
    suspend fun insertSteps(steps: List<CycleStepEntity>)

    @Query("UPDATE cycle_step SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setStepPosition(id: String, position: Int, now: Long)

    @Query("UPDATE cycle_step SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteStep(id: String, now: Long)

    /** UNDO of a removed step: the row comes back at [position]. */
    @Query("UPDATE cycle_step SET deletedAt = NULL, position = :position, updatedAt = :now WHERE id = :id")
    suspend fun restoreStep(id: String, position: Int, now: Long)

    @Query("UPDATE cycle_step SET deletedAt = :now, updatedAt = :now WHERE programId = :programId AND deletedAt IS NULL")
    suspend fun softDeleteStepsOfProgram(programId: String, now: Long)
}
