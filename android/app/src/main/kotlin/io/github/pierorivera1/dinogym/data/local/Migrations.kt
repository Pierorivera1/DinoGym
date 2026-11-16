package io.github.pierorivera1.dinogym.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/*
 * Schema migrations. The app holds real workout history, so a schema change never drops
 * the database: each version step gets a hand-written migration, checked against the
 * exported schemas in `android/app/schemas/` by `DinoDatabaseMigrationTest` (CI).
 */

/**
 * 1 → 2 (task 11): the `weigh_in` table and the synced `preference` table. Both are new and
 * empty, so no existing row changes. The SQL matches the `createSql` in the exported `2.json`.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `weigh_in` (`id` TEXT NOT NULL, `measuredAt` INTEGER NOT NULL, " +
                "`weightKg` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_weigh_in_measuredAt` ON `weigh_in` (`measuredAt`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `preference` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`key`))",
        )
    }
}

/** Every migration, in order, for the database builder and the migration test. */
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2)
