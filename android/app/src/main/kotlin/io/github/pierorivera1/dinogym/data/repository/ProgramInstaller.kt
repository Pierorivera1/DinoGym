package io.github.pierorivera1.dinogym.data.repository

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.CycleStepEntity
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ExerciseEntity
import io.github.pierorivera1.dinogym.data.local.ProgramEntity
import io.github.pierorivera1.dinogym.data.local.RoutineEntity
import io.github.pierorivera1.dinogym.data.local.RoutineExerciseEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramTemplate
import io.github.pierorivera1.dinogym.domain.rules.InstallRules
import io.github.pierorivera1.dinogym.domain.rules.TextFold
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a [ProgramTemplate] into the database as new rows (decision 93): the first-run seed,
 * a starter program, and later an imported share all go through here.
 *
 * - Every program, routine, step and routine line gets a new id, so it syncs like any row
 *   the user made.
 * - An exercise reuses an active library exercise with the same name and kind
 *   ([InstallRules.match]); otherwise it is created. Inside one template, the same exercise
 *   used by two routines is one row.
 * - Routine and program names that are taken get a number ([InstallRules.uniqueName]).
 */
@Singleton
class ProgramInstaller @Inject constructor(
    private val db: DinoDatabase,
    private val clock: Clock,
) {

    enum class Activation {
        /** Active only when no program is (decision 48), so Home is never left empty. */
        IF_NONE_ACTIVE,

        /** Always the active program (the first-run seed). */
        ALWAYS,
    }

    /** Installs [template] in its own transaction and returns the new program's id. */
    suspend fun install(template: ProgramTemplate, activation: Activation = Activation.IF_NONE_ACTIVE): String =
        db.withWriteTransaction { installInTransaction(template, activation) }

    /** The same as [install], for a caller that already holds a write transaction. */
    suspend fun installInTransaction(template: ProgramTemplate, activation: Activation): String {
        val now = clock.millis()
        val exerciseIds = exerciseIds(template, now)

        val takenRoutines = db.routineDao().getNames().toMutableList()
        val routineIds = template.routines.map { newId() }
        db.routineDao().insertAll(
            template.routines.mapIndexed { i, routine ->
                val name = InstallRules.uniqueName(routine.name, takenRoutines).also { takenRoutines += it }
                RoutineEntity(id = routineIds[i], name = name, updatedAt = now)
            },
        )
        db.routineDao().insertItems(
            template.routines.flatMapIndexed { i, routine ->
                routine.items.mapIndexed { position, item ->
                    RoutineExerciseEntity(
                        id = newId(),
                        routineId = routineIds[i],
                        exerciseId = exerciseIds.getValue(key(item.exercise)),
                        position = position,
                        sets = item.sets,
                        repMin = item.repMin,
                        repMax = item.repMax,
                        restSeconds = item.restSeconds,
                        updatedAt = now,
                    )
                }
            },
        )

        val programs = db.programDao()
        val activate = activation == Activation.ALWAYS || programs.getActive() == null
        if (activate) programs.clearActive(now)
        val programId = newId()
        programs.insert(
            ProgramEntity(
                id = programId,
                name = InstallRules.uniqueName(template.name, programs.getNames()),
                type = template.type.name,
                currentStep = 0,
                isActive = activate,
                updatedAt = now,
            ),
        )
        programs.insertSteps(
            template.cycle.mapIndexed { position, routineIndex ->
                CycleStepEntity(
                    id = newId(),
                    programId = programId,
                    position = position,
                    routineId = routineIndex?.let { routineIds[it] },
                    updatedAt = now,
                )
            },
        )
        return programId
    }

    /** The library id for each distinct exercise of [template]: matched, or inserted now. */
    private suspend fun exerciseIds(template: ProgramTemplate, now: Long): Map<Pair<String, String>, String> {
        val library = db.exerciseDao().getAll().map { it.toDomain() }
        val wanted = template.routines.flatMap { r -> r.items.map { it.exercise } }.distinctBy(::key)
        val ids = mutableMapOf<Pair<String, String>, String>()
        val created = mutableListOf<ExerciseEntity>()
        wanted.forEach { exercise ->
            val match = InstallRules.match(library, exercise)
            ids[key(exercise)] = match?.id ?: newId().also { id ->
                created += ExerciseEntity(
                    id = id,
                    name = exercise.name,
                    iconKey = exercise.icon.name,
                    kind = exercise.kind.name,
                    template = exercise.template,
                    weightEnabled = exercise.weightEnabled,
                    archived = false,
                    updatedAt = now,
                )
            }
        }
        db.exerciseDao().insertAll(created)
        return ids
    }

    private fun key(exercise: ExerciseTemplate) = TextFold.fold(exercise.name) to exercise.kind.name
}
