package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.api.models.ChangeSet
import io.github.pierorivera1.dinogym.api.models.CycleStep
import io.github.pierorivera1.dinogym.api.models.Exercise
import io.github.pierorivera1.dinogym.api.models.Preference
import io.github.pierorivera1.dinogym.api.models.Program
import io.github.pierorivera1.dinogym.api.models.Routine
import io.github.pierorivera1.dinogym.api.models.RoutineExercise
import io.github.pierorivera1.dinogym.api.models.SetEntry
import io.github.pierorivera1.dinogym.api.models.SyncTable
import io.github.pierorivera1.dinogym.api.models.WeighIn
import io.github.pierorivera1.dinogym.api.models.WorkoutSession
import io.github.pierorivera1.dinogym.data.local.CycleStepEntity
import io.github.pierorivera1.dinogym.data.local.ExerciseEntity
import io.github.pierorivera1.dinogym.data.local.PreferenceEntity
import io.github.pierorivera1.dinogym.data.local.ProgramEntity
import io.github.pierorivera1.dinogym.data.local.RoutineEntity
import io.github.pierorivera1.dinogym.data.local.RoutineExerciseEntity
import io.github.pierorivera1.dinogym.data.local.RowVersion
import io.github.pierorivera1.dinogym.data.local.SessionEntity
import io.github.pierorivera1.dinogym.data.local.SetEntryEntity
import io.github.pierorivera1.dinogym.data.local.WeighInEntity

/** One synced row: which table, its id (a preference's key), and the row it points to. */
data class RowRef(val table: SyncTable, val id: String)

/**
 * A synced row as sync handles it: the Room entity, plus what the engine needs without
 * knowing the table (its ref, version and parent rows). Built from Room for a push and from
 * the API's `ChangeSet` for a pull.
 */
sealed interface SyncRow {
    val ref: RowRef
    val version: RowVersion

    /** The rows this one points to by foreign key; they must exist before it is written. */
    val parents: List<RowRef>
}

data class ProgramRow(val entity: ProgramEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.PROGRAM, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = emptyList<RowRef>()
}

data class CycleStepRow(val entity: CycleStepEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.CYCLE_STEP, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = listOfNotNull(
        RowRef(SyncTable.PROGRAM, entity.programId),
        entity.routineId?.let { RowRef(SyncTable.ROUTINE, it) },
    )
}

data class RoutineRow(val entity: RoutineEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.ROUTINE, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = emptyList<RowRef>()
}

data class ExerciseRow(val entity: ExerciseEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.EXERCISE, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = emptyList<RowRef>()
}

data class RoutineExerciseRow(val entity: RoutineExerciseEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.ROUTINE_EXERCISE, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = listOf(
        RowRef(SyncTable.ROUTINE, entity.routineId),
        RowRef(SyncTable.EXERCISE, entity.exerciseId),
    )
}

data class SessionRow(val entity: SessionEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.SESSION, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = listOf(
        RowRef(SyncTable.ROUTINE, entity.routineId),
        RowRef(SyncTable.PROGRAM, entity.programId),
    )
}

data class SetEntryRow(val entity: SetEntryEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.SET_ENTRY, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = listOf(
        RowRef(SyncTable.SESSION, entity.sessionId),
        RowRef(SyncTable.EXERCISE, entity.exerciseId),
    )
}

data class WeighInRow(val entity: WeighInEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.WEIGH_IN, entity.id)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = emptyList<RowRef>()
}

data class PreferenceRow(val entity: PreferenceEntity) : SyncRow {
    override val ref get() = RowRef(SyncTable.PREFERENCE, entity.key)
    override val version get() = RowVersion(entity.updatedAt, entity.deletedAt)
    override val parents get() = emptyList<RowRef>()
}

/**
 * Parents before children, so a page applied in this order never writes a row before the
 * row it points to (decision 89). Pushes use the same order.
 */
val TABLE_ORDER: List<SyncTable> = listOf(
    SyncTable.PROGRAM,
    SyncTable.ROUTINE,
    SyncTable.EXERCISE,
    SyncTable.CYCLE_STEP,
    SyncTable.ROUTINE_EXERCISE,
    SyncTable.SESSION,
    SyncTable.SET_ENTRY,
    SyncTable.WEIGH_IN,
    SyncTable.PREFERENCE,
)

/** Sorts rows parents-first, keeping the order within each table. */
fun <T> List<T>.inTableOrder(ref: (T) -> RowRef): List<T> = sortedBy { TABLE_ORDER.indexOf(ref(it).table) }

/** Every row of a pulled (or stored) change set, parents first. */
fun ChangeSet.toRows(): List<SyncRow> = buildList {
    programs.orEmpty().forEach { add(ProgramRow(it.toEntity())) }
    routines.orEmpty().forEach { add(RoutineRow(it.toEntity())) }
    exercises.orEmpty().forEach { add(ExerciseRow(it.toEntity())) }
    cycleSteps.orEmpty().forEach { add(CycleStepRow(it.toEntity())) }
    routineExercises.orEmpty().forEach { add(RoutineExerciseRow(it.toEntity())) }
    sessions.orEmpty().forEach { add(SessionRow(it.toEntity())) }
    setEntries.orEmpty().forEach { add(SetEntryRow(it.toEntity())) }
    weighIns.orEmpty().forEach { add(WeighInRow(it.toEntity())) }
    preferences.orEmpty().forEach { add(PreferenceRow(it.toEntity())) }
}

/** The rows as one change set for `/sync/push`. Empty tables are left out. */
fun List<SyncRow>.toChangeSet(): ChangeSet {
    fun <R : SyncRow, M> pick(type: Class<R>, map: (R) -> M): List<M>? =
        filterIsInstance(type).map(map).ifEmpty { null }
    return ChangeSet(
        programs = pick(ProgramRow::class.java) { it.entity.toApi() },
        cycleSteps = pick(CycleStepRow::class.java) { it.entity.toApi() },
        routines = pick(RoutineRow::class.java) { it.entity.toApi() },
        exercises = pick(ExerciseRow::class.java) { it.entity.toApi() },
        routineExercises = pick(RoutineExerciseRow::class.java) { it.entity.toApi() },
        sessions = pick(SessionRow::class.java) { it.entity.toApi() },
        setEntries = pick(SetEntryRow::class.java) { it.entity.toApi() },
        weighIns = pick(WeighInRow::class.java) { it.entity.toApi() },
        preferences = pick(PreferenceRow::class.java) { it.entity.toApi() },
    )
}

// Room ↔ API. Field names are the same on both sides (decision 62); only the enums differ.

private fun ProgramEntity.toApi() = Program(
    id = id, name = name, type = Program.Type.entries.first { it.value == type },
    currentStep = currentStep, isActive = isActive, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun Program.toEntity() = ProgramEntity(
    id = id, name = name, type = type.value, currentStep = currentStep, isActive = isActive,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun CycleStepEntity.toApi() = CycleStep(
    id = id, programId = programId, position = position, routineId = routineId,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun CycleStep.toEntity() = CycleStepEntity(
    id = id, programId = programId, position = position, routineId = routineId,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun RoutineEntity.toApi() = Routine(id = id, name = name, updatedAt = updatedAt, deletedAt = deletedAt)

private fun Routine.toEntity() = RoutineEntity(id = id, name = name, updatedAt = updatedAt, deletedAt = deletedAt)

private fun ExerciseEntity.toApi() = Exercise(
    id = id, name = name, iconKey = iconKey, kind = Exercise.Kind.entries.first { it.value == kind },
    template = template, weightEnabled = weightEnabled, archived = archived,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun Exercise.toEntity() = ExerciseEntity(
    id = id, name = name, iconKey = iconKey, kind = kind.value, template = template,
    weightEnabled = weightEnabled, archived = archived, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun RoutineExerciseEntity.toApi() = RoutineExercise(
    id = id, routineId = routineId, exerciseId = exerciseId, position = position, sets = sets,
    repMin = repMin, repMax = repMax, restSeconds = restSeconds, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun RoutineExercise.toEntity() = RoutineExerciseEntity(
    id = id, routineId = routineId, exerciseId = exerciseId, position = position, sets = sets,
    repMin = repMin, repMax = repMax, restSeconds = restSeconds, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun SessionEntity.toApi() = WorkoutSession(
    id = id, routineId = routineId, programId = programId, startedAt = startedAt, endedAt = endedAt,
    currentPosition = currentPosition, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun WorkoutSession.toEntity() = SessionEntity(
    id = id, routineId = routineId, programId = programId, startedAt = startedAt, endedAt = endedAt,
    currentPosition = currentPosition, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun SetEntryEntity.toApi() = SetEntry(
    id = id, sessionId = sessionId, exerciseId = exerciseId, setIndex = setIndex, reps = reps,
    weightKg = weightKg, note = note, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun SetEntry.toEntity() = SetEntryEntity(
    id = id, sessionId = sessionId, exerciseId = exerciseId, setIndex = setIndex, reps = reps,
    weightKg = weightKg, note = note, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun WeighInEntity.toApi() =
    WeighIn(id = id, measuredAt = measuredAt, weightKg = weightKg, updatedAt = updatedAt, deletedAt = deletedAt)

private fun WeighIn.toEntity() =
    WeighInEntity(id = id, measuredAt = measuredAt, weightKg = weightKg, updatedAt = updatedAt, deletedAt = deletedAt)

private fun PreferenceEntity.toApi() = Preference(key = key, value = value, updatedAt = updatedAt, deletedAt = deletedAt)

private fun Preference.toEntity() = PreferenceEntity(key = key, value = value, updatedAt = updatedAt, deletedAt = deletedAt)
