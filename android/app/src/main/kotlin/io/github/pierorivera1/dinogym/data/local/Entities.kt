package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * Room tables (ARCHITECTURE.md, "Data model").
 *
 * Every synced table has a text UUID id, `updatedAt` and a nullable `deletedAt`
 * (decision 36): ids from two phones never collide, and a soft delete is a row
 * change that sync can send later. Times are epoch milliseconds.
 *
 * Enums are stored as their name in a TEXT column and converted in Mappers.kt,
 * so the database stays readable and no type converter is needed.
 *
 * Foreign keys use NO ACTION: rows are only soft-deleted, so a hard delete of a
 * parent that still has children is a bug and SQLite refuses it.
 */

@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currentStep: Int,
    val isActive: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "cycle_step",
    foreignKeys = [
        ForeignKey(entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"]),
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"]),
    ],
    indices = [Index("programId", "position"), Index("routineId")],
)
data class CycleStepEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val position: Int,
    /** Null is a rest day. */
    val routineId: String?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "exercise", indices = [Index("archived")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val kind: String,
    val template: String?,
    val weightEnabled: Boolean,
    val archived: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"]),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("routineId", "position"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"]),
        ForeignKey(entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"]),
    ],
    indices = [Index("routineId", "endedAt"), Index("programId"), Index("endedAt")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val programId: String,
    val startedAt: Long,
    /** Null while the workout is in progress. */
    val endedAt: Long?,
    /** Index into the routine's exercise list where the workout in progress is. */
    val currentPosition: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"]),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("sessionId", "exerciseId", "setIndex"), Index("exerciseId", "setIndex")],
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    /** 1-based. */
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val note: String?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Local-only flags for this install, such as "the first-run seed has run".
 * Never synced, so it has no UUID, `updatedAt` or `deletedAt`. Keeping the flag
 * in the same database lets the seed and its flag be written in one transaction.
 */
@Entity(tableName = "local_flag")
data class LocalFlagEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** One body weight reading (task 11). Several on the same day are allowed; the chart averages them. */
@Entity(tableName = "weigh_in", indices = [Index("measuredAt")])
data class WeighInEntity(
    @PrimaryKey val id: String,
    /** When the weight was taken, epoch milliseconds. A past day picked in the sheet keeps the time of day it was logged. */
    val measuredAt: Long,
    val weightKg: Double,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A user preference that follows the account across phones (the first one is the body
 * weight goal). Keyed by name instead of a UUID: the same setting on two phones is one row,
 * and sync keeps the newest `updatedAt`. Clearing a preference is a soft delete. Values are
 * text; each key's owner converts it. Local-only settings belong in `local_flag`.
 */
@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A synced row changed on this phone since its last successful push (task 21, decision 88).
 * Filled by SQLite triggers on every synced table (SyncTracking.kt), so no write path can
 * forget it. One entry per changed row: a new change replaces the old entry and takes a higher
 * [seq], so a push clears only the entry it sent, and an edit made during the push stays.
 * Local-only, never synced.
 */
@Entity(tableName = "sync_pending", indices = [Index("tableName", "rowId", unique = true)])
data class SyncPendingEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** The table's name, as in the API's `SyncTable`. */
    val tableName: String,
    /** The row's id, or a preference's key. */
    val rowId: String,
)

/**
 * A pulled row whose parent row has not arrived yet (task 21, decision 89): a set whose
 * session is on a later page, or still inside the server's 30-second window (decision 86).
 * Kept as the JSON of a one-row `ChangeSet` and tried again after every pulled page. Local-only.
 */
@Entity(tableName = "sync_inbox", primaryKeys = ["tableName", "rowId"])
data class SyncInboxEntity(
    val tableName: String,
    val rowId: String,
    val json: String,
)
