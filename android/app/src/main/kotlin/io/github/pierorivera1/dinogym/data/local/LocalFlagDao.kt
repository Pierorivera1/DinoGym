package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

/** Reads and writes local-only flags of this install. */
@Dao
interface LocalFlagDao {

    @Query("SELECT value FROM local_flag WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(flag: LocalFlagEntity)
}
