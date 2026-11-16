package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** Weigh-ins (task 11). Deleting is soft, so UNDO and sync can bring a row back. */
@Dao
interface WeighInDao {

    /** Every weigh-in that is not deleted, newest first. */
    @Query("SELECT * FROM weigh_in WHERE deletedAt IS NULL ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<WeighInEntity>>

    /** The latest weigh-in, so the log sheet can start from it. */
    @Query("SELECT * FROM weigh_in WHERE deletedAt IS NULL ORDER BY measuredAt DESC LIMIT 1")
    suspend fun getLatest(): WeighInEntity?

    @Insert
    suspend fun insert(weighIn: WeighInEntity)

    /** Soft delete with [deletedAt] set to now, or undo it with null. */
    @Query("UPDATE weigh_in SET deletedAt = :deletedAt, updatedAt = :now WHERE id = :id")
    suspend fun setDeleted(id: String, deletedAt: Long?, now: Long)
}

/** Synced user preferences, by key. */
@Dao
interface PreferenceDao {

    /** The value of [key], or null when it was never set or was cleared. */
    @Query("SELECT value FROM preference WHERE `key` = :key AND deletedAt IS NULL")
    fun observe(key: String): Flow<String?>

    /** Sets a value; a cleared row comes back with `deletedAt` null. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: PreferenceEntity)

    @Query("UPDATE preference SET deletedAt = :now, updatedAt = :now WHERE `key` = :key AND deletedAt IS NULL")
    suspend fun clear(key: String, now: Long)
}
