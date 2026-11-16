package io.github.pierorivera1.dinogym.data.repository

import io.github.pierorivera1.dinogym.data.local.ExerciseDao
import io.github.pierorivera1.dinogym.data.local.ExerciseEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.domain.model.Exercise
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The exercise library. Exercises are archived instead of deleted, because their
 * history must stay readable; an archived exercise can be restored.
 */
@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
) {

    /** Active exercises when [archived] is false, archived ones when true; sorted by name. */
    fun observeExercises(archived: Boolean = false): Flow<List<Exercise>> =
        exerciseDao.observeByArchived(archived).map { rows -> rows.map { it.toDomain() } }

    fun observeExercise(id: String): Flow<Exercise?> =
        exerciseDao.observeById(id).map { it?.toDomain() }

    /** Names of the routines that use exercise [id], sorted ("Pull", "Upper"). */
    fun observeRoutineNamesUsing(id: String): Flow<List<String>> = exerciseDao.observeRoutineNamesUsing(id)

    /**
     * Creates an exercise with the defaults the `+ NEW` button uses: the generic icon, logged
     * as reps, no weight. Returns its id, so the editor can open it.
     */
    suspend fun createDefaultExercise(name: String): String =
        createExercise(name, ExerciseIcon.GENERIC, ExerciseKind.REPS, template = null, weightEnabled = false)

    /** Creates an active exercise and returns its id. [template] is only used by [ExerciseKind.TEMPLATE]. */
    suspend fun createExercise(
        name: String,
        icon: ExerciseIcon,
        kind: ExerciseKind,
        template: String?,
        weightEnabled: Boolean,
    ): String {
        val id = newId()
        exerciseDao.insert(
            ExerciseEntity(
                id = id,
                name = name,
                iconKey = icon.name,
                kind = kind.name,
                template = template,
                weightEnabled = weightEnabled,
                archived = false,
                updatedAt = clock.millis(),
            ),
        )
        return id
    }

    /** Saves name, icon, kind, template and weight switch. Archiving has its own call. */
    suspend fun updateExercise(exercise: Exercise) = exerciseDao.update(
        id = exercise.id,
        name = exercise.name,
        iconKey = exercise.icon.name,
        kind = exercise.kind.name,
        template = exercise.template,
        weightEnabled = exercise.weightEnabled,
        now = clock.millis(),
    )

    suspend fun setArchived(id: String, archived: Boolean) = exerciseDao.setArchived(id, archived, clock.millis())
}
