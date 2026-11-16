package io.github.pierorivera1.dinogym.data.local

import io.github.pierorivera1.dinogym.domain.model.CycleStep
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.domain.model.Program
import io.github.pierorivera1.dinogym.domain.model.ProgramType
import io.github.pierorivera1.dinogym.domain.model.Routine
import io.github.pierorivera1.dinogym.domain.model.RoutineExercise
import io.github.pierorivera1.dinogym.domain.model.Session
import io.github.pierorivera1.dinogym.domain.model.SetEntry
import io.github.pierorivera1.dinogym.domain.model.SetOneResult
import java.util.UUID

/* Conversions between Room rows and domain models. Sync columns stay in the data layer. */

/** A new random UUID, the primary key format of every synced table (decision 36). */
fun newId(): String = UUID.randomUUID().toString()

fun ProgramEntity.toDomain() = Program(
    id = id,
    name = name,
    type = ProgramType.entries.firstOrNull { it.name == type } ?: ProgramType.CALISTHENICS,
    currentStep = currentStep,
    isActive = isActive,
)

fun CycleStepEntity.toDomain() = CycleStep(id = id, programId = programId, position = position, routineId = routineId)

fun CycleStepRow.toDomain() = CycleStep(id = id, programId = programId, position = position, routineId = routineId)

fun RoutineEntity.toDomain() = Routine(id = id, name = name)

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    icon = ExerciseIcon.fromKey(iconKey),
    kind = ExerciseKind.fromKey(kind),
    template = template,
    weightEnabled = weightEnabled,
    archived = archived,
)

fun RoutineExerciseEntity.toDomain() = RoutineExercise(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    position = position,
    sets = sets,
    repMin = repMin,
    repMax = repMax,
    restSeconds = restSeconds,
)

fun SessionEntity.toDomain() = Session(
    id = id,
    routineId = routineId,
    programId = programId,
    startedAt = startedAt,
    endedAt = endedAt,
    currentPosition = currentPosition,
)

fun SessionListRow.toDomain() = Session(
    id = id,
    routineId = routineId,
    programId = programId,
    startedAt = startedAt,
    endedAt = endedAt,
    currentPosition = 0,
)

fun SetEntryEntity.toDomain() = SetEntry(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setIndex = setIndex,
    reps = reps,
    weightKg = weightKg,
    note = note,
)

fun SetOneRow.toDomain() = SetOneResult(sessionId = sessionId, date = date, reps = reps, weightKg = weightKg)
