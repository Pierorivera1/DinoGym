package io.github.pierorivera1.dinogym.data.local

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Change tracking for sync (task 21, decision 88): an `AFTER INSERT` and an `AFTER UPDATE`
 * trigger on every synced table write `(table, id)` into `sync_pending`. `INSERT OR REPLACE`
 * against its unique index gives the entry a new, higher `seq` on every change.
 *
 * Triggers instead of a call in each DAO method: there are dozens of write paths (logging a
 * set, UNDO, reordering, the seed), and a trigger cannot be forgotten by the next one.
 * Rows are never hard-deleted (soft delete, decision 36), so there is no `DELETE` trigger.
 * `local_flag`, `sync_pending` and `sync_inbox` have none: they never sync.
 *
 * Room does not manage triggers, so they are created with `IF NOT EXISTS` every time the
 * database opens. Changing one later needs a `DROP TRIGGER` in that version's migration.
 */
object SyncTracking {

    /** Each synced table and its key column. */
    val TRACKED: List<Pair<String, String>> = listOf(
        "program" to "id",
        "cycle_step" to "id",
        "routine" to "id",
        "exercise" to "id",
        "routine_exercise" to "id",
        "session" to "id",
        "set_entry" to "id",
        "weigh_in" to "id",
        "preference" to "key",
    )

    fun triggerSql(): List<String> = TRACKED.flatMap { (table, key) ->
        listOf("INSERT", "UPDATE").map { event ->
            "CREATE TRIGGER IF NOT EXISTS `sync_track_${table}_${event.lowercase()}` AFTER $event ON `$table` " +
                "BEGIN INSERT OR REPLACE INTO `sync_pending` (`tableName`, `rowId`) VALUES ('$table', NEW.`$key`); END"
        }
    }

    /** Add to every builder of [DinoDatabase], including tests'. */
    val callback = object : RoomDatabase.Callback() {
        override suspend fun onOpen(connection: SQLiteConnection) {
            triggerSql().forEach { connection.execSQL(it) }
        }
    }
}
