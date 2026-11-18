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

/**
 * 2 → 3 (task 21): the local-only sync tables `sync_pending` and `sync_inbox`. No existing
 * row changes and nothing is marked pending here: the first sign-in marks every row
 * (decision 90). The triggers that fill `sync_pending` are not part of Room's schema; they
 * are created when the database opens ([SyncTracking]).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_pending` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tableName` TEXT NOT NULL, `rowId` TEXT NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_pending_tableName_rowId` ON `sync_pending` (`tableName`, `rowId`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_inbox` (`tableName` TEXT NOT NULL, `rowId` TEXT NOT NULL, " +
                "`json` TEXT NOT NULL, PRIMARY KEY(`tableName`, `rowId`))",
        )
    }
}

/** Every migration, in order, for the database builder and the migration test. */
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)
