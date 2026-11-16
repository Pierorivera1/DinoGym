package io.github.pierorivera1.dinogym.data.seed

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.CycleStepEntity
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ExerciseEntity
import io.github.pierorivera1.dinogym.data.local.LocalFlagEntity
import io.github.pierorivera1.dinogym.data.local.ProgramEntity
import io.github.pierorivera1.dinogym.data.local.RoutineEntity
import io.github.pierorivera1.dinogym.data.local.RoutineExerciseEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts the owner's program ([OwnerProgramSeed]) on the first launch, and never again.
 *
 * "Never again" is a row in the `local_flag` table, written in the same transaction
 * as the seed. Either both are saved or neither is, so a crash halfway retries on
 * the next launch, and deleting every program later does not bring the seed back.
 * Clearing the app's data removes the flag too, which counts as a new install.
 */
@Singleton
class FirstRunSeeder @Inject constructor(
    private val db: DinoDatabase,
    private val strings: SeedStrings,
    private val clock: Clock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    /** Starts [seedIfNeeded] in the background; called from `DinoGymApp.onCreate`. */
    fun seedInBackground() {
        appScope.launch { seedIfNeeded() }
    }

    /** Inserts the seed unless it ran before. Returns true when it inserted it. */
    suspend fun seedIfNeeded(): Boolean = db.withWriteTransaction {
        val flags = db.localFlagDao()
        if (flags.get(SEEDED_FLAG) != null) return@withWriteTransaction false
        insert(OwnerProgramSeed.program, clock.millis())
        flags.put(LocalFlagEntity(SEEDED_FLAG, clock.millis().toString()))
        true
    }

    private suspend fun insert(seed: SeedProgram, now: Long) {
        val exerciseIds = seed.exercises.associate { it.name to newId() }
        val routineIds = seed.routines.associate { it.name to newId() }
        val programId = newId()

        db.exerciseDao().insertAll(
            seed.exercises.map { exercise ->
                ExerciseEntity(
                    id = exerciseIds.getValue(exercise.name),
                    name = strings.get(exercise.name),
                    iconKey = exercise.icon.name,
                    kind = exercise.kind.name,
                    template = exercise.template?.let { strings.get(it) },
                    weightEnabled = false,
                    archived = false,
                    updatedAt = now,
                )
            },
        )

        db.routineDao().insertAll(
            seed.routines.map { RoutineEntity(id = routineIds.getValue(it.name), name = strings.get(it.name), updatedAt = now) },
        )
        db.routineDao().insertItems(
            seed.routines.flatMap { routine ->
                routine.items.mapIndexed { position, item ->
                    RoutineExerciseEntity(
                        id = newId(),
                        routineId = routineIds.getValue(routine.name),
                        exerciseId = exerciseIds.getValue(item.exercise),
                        position = position,
                        sets = item.sets,
                        repMin = item.reps,
                        repMax = item.reps,
                        restSeconds = item.restSeconds,
                        updatedAt = now,
                    )
                }
            },
        )

        db.programDao().clearActive(now)
        db.programDao().insert(
            ProgramEntity(
                id = programId,
                name = strings.get(seed.name),
                type = seed.type.name,
                currentStep = 0,
                isActive = true,
                updatedAt = now,
            ),
        )
        db.programDao().insertSteps(
            seed.cycle.mapIndexed { position, routine ->
                CycleStepEntity(
                    id = newId(),
                    programId = programId,
                    position = position,
                    routineId = routine?.let { routineIds.getValue(it) },
                    updatedAt = now,
                )
            },
        )
    }

    companion object {
        const val SEEDED_FLAG = "first_run_seed_done"
    }
}
