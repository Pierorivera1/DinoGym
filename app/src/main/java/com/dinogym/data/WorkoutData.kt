@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.dinogym.data

import kotlinx.serialization.Serializable
import java.util.UUID

val routines = listOf("Pull", "Push", "Legs", "Upper")

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val template: String = "{n} reps",
    val unit: String = "reps",
    val archived: Boolean = false,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 80)
        require(template.length <= 120 && template.split("{n}").size == 2)
        require(unit.isNotBlank() && unit.length <= 20)
    }
    fun display(value: Int) = template.replace("{n}", value.toString())
}

@Serializable
data class SetRecord(val value: Int, val weightKg: Double? = null) {
    init {
        require(value in 0..9999)
        require(weightKg == null || (weightKg.isFinite() && weightKg in 0.0..9999.0))
    }
}

@Serializable
data class Entry(val exercise: Exercise, val records: List<SetRecord>, val notes: String = "") {
    constructor(exercise: Exercise, value: Int, sets: Int, notes: String = "") :
        this(exercise, List(sets.also { require(it in 1..99) }) { SetRecord(value) }, notes)
    init { require(records.size in 1..99 && notes.length <= 500) }
    val sets get() = records.size
    val value get() = records.maxOf { it.value }
    val display get() = if (records.distinct().size == 1 && records.first().weightKg == null)
        "${exercise.display(value)} × $sets"
    else records.mapIndexed { index, set ->
        "${index + 1}. ${exercise.display(set.value)}" + (set.weightKg?.let { " · $it kg" } ?: "")
    }.joinToString("\n")
}

@Serializable
data class Session(
    val routine: String,
    val entries: List<Entry>,
    @kotlinx.serialization.EncodeDefault val performedAt: Long = System.currentTimeMillis(),
    @kotlinx.serialization.EncodeDefault val id: String = UUID.randomUUID().toString(),
    val workoutId: String? = null,
) {
    init {
        require(id.isNotBlank() && routine.isNotBlank() && routine.length <= 80 && entries.isNotEmpty())
        require(entries.map { it.exercise.id }.distinct().size == entries.size)
    }
}

@Serializable
data class WorkoutData(
    val exercises: List<Exercise> = starterExercises,
    val sessions: List<Session> = emptyList(),
    val workouts: List<WorkoutTemplate> = starterWorkouts,
    val splits: List<TrainingSplit> = listOf(TrainingSplit("starter-split", "My training split", starterWorkouts.map { it.id })),
    val activeSplitId: String? = "starter-split",
    val nextWorkoutIndex: Int = 0,
    @kotlinx.serialization.EncodeDefault val exercisePresetVersion: Int = 1,
    @kotlinx.serialization.EncodeDefault val schemaVersion: Int = 2,
) {
    init {
        require(schemaVersion == 2 && nextWorkoutIndex >= 0)
        require(sessions.distinctBy { it.id }.size == sessions.size)
        require(exercises.distinctBy { it.id }.size == exercises.size)
        require(workouts.distinctBy { it.id }.size == workouts.size)
        require(splits.distinctBy { it.id }.size == splits.size)
    }
    val activeSplit get() = splits.find { it.id == activeSplitId }
    val nextWorkout get() = activeSplit?.workoutIds?.let { ids ->
        ids.getOrNull(nextWorkoutIndex % ids.size.coerceAtLeast(1))
    }?.let { id -> workouts.find { it.id == id } }

    fun saveWorkout(workout: WorkoutTemplate) = copy(workouts =
        if (workouts.any { it.id == workout.id }) workouts.map { if (it.id == workout.id) workout else it }
        else workouts + workout)

    fun saveSplit(split: TrainingSplit) = copy(splits =
        if (splits.any { it.id == split.id }) splits.map { if (it.id == split.id) split else it }
        else splits + split, nextWorkoutIndex = if (activeSplitId == split.id) 0 else nextWorkoutIndex)

    fun selectSplit(id: String): WorkoutData {
        require(splits.any { it.id == id })
        return copy(activeSplitId = id, nextWorkoutIndex = 0)
    }

    fun finishSession(session: Session, updateTargets: Boolean, rests: Map<String, Int> = emptyMap()): WorkoutData {
        if (sessions.any { it.id == session.id }) return saveSession(session)
        var result = saveSession(session)
        if (session.workoutId == nextWorkout?.id) result = result.copy(
            nextWorkoutIndex = (nextWorkoutIndex + 1) % activeSplit!!.workoutIds.size)
        if (updateTargets) workouts.find { it.id == session.workoutId }?.let { workout ->
            result = result.saveWorkout(workout.copy(exercises = workout.exercises.map { planned ->
                session.entries.find { it.exercise.id == planned.exerciseId }?.let {
                    planned.copy(targets = it.records, restSeconds = rests[planned.exerciseId] ?: planned.restSeconds)
                } ?: planned
            } + session.entries.filter { e -> workout.exercises.none { it.exerciseId == e.exercise.id } }
                .map { PlannedExercise(it.exercise.id, it.records, rests[it.exercise.id] ?: 165) }))
        }
        return result
    }

    val progressExercises get() = (exercises + sessions.flatMap { session -> session.entries.map { it.exercise } })
        .distinctBy { it.id }

    fun updateExercise(exercise: Exercise) = copy(exercises = exercises.map {
        if (it.id == exercise.id) exercise else it
    })

    fun archiveExercise(exerciseId: String) = copy(exercises = exercises.map {
        if (it.id == exerciseId) it.copy(archived = true) else it
    })

    fun restoreExercise(exerciseId: String) = copy(exercises = exercises.map {
        if (it.id == exerciseId) it.copy(archived = false) else it
    })

    fun saveSession(session: Session): WorkoutData {
        val original = sessions.find { it.id == session.id }
        return copy(sessions = if (original == null) sessions + session else sessions.map {
            if (it.id == session.id) session.copy(performedAt = original.performedAt) else it
        })
    }

    fun deleteSession(sessionId: String) = copy(sessions = sessions.filterNot { it.id == sessionId })

    fun history(exerciseId: String) = sessions.sortedBy { it.performedAt }
        .mapNotNull { session -> session.entries.find { it.exercise.id == exerciseId }?.let { session to it } }
}

private val originalExercises = listOf(
    Exercise("pull-ups", "Pull-ups"),
    Exercise("front-pulls", "Front holds + pulls", "3s + {n} pulls + 3s", "pulls"),
    Exercise("rows", "Australian rows"),
    Exercise("dips", "Dips"),
    Exercise("push-ups", "Push-ups"),
    Exercise("pike", "Pike push-ups"),
    Exercise("squats", "Squats"),
    Exercise("lunges", "Lunges"),
    Exercise("calf", "Calf raises"),
)

internal val currentExercises = listOf(
    Exercise("forearm-curl", "Forearm curl"),
    Exercise("reverse-curl", "Reverse curl"),
    Exercise("bicep-curl", "Bicep curl"),
    Exercise("feet-elevated-push-ups", "Feet-elevated push-ups"),
    Exercise("single-arm-lateral-raises", "Single-arm lateral raises"),
    Exercise("lateral-raises", "Lateral raises"),
    Exercise("hollow-push-ups", "Hollow push-ups"),
    Exercise("neutral-grip-pull-ups", "Neutral-grip pull-ups"),
)

val starterExercises = originalExercises + currentExercises

val starterWorkouts = listOf(
    WorkoutTemplate("Pull", "Pull", listOf(
        PlannedExercise("pull-ups"), PlannedExercise("front-pulls"),
        PlannedExercise("rows", List(3) { SetRecord(12) }),
        PlannedExercise("forearm-curl", List(3) { SetRecord(13) }),
        PlannedExercise("reverse-curl", List(2) { SetRecord(25) }),
        PlannedExercise("bicep-curl", List(2) { SetRecord(12) }),
    )),
    WorkoutTemplate("Push", "Push", listOf(
        PlannedExercise("dips"), PlannedExercise("push-ups"), PlannedExercise("pike"),
        PlannedExercise("feet-elevated-push-ups", List(3) { SetRecord(7) }),
        PlannedExercise("single-arm-lateral-raises", List(2) { SetRecord(10) }),
        PlannedExercise("lateral-raises", List(2) { SetRecord(14) }),
        PlannedExercise("hollow-push-ups", List(2) { SetRecord(0) }, maxReps = true),
    )),
    WorkoutTemplate("Legs", "Legs", listOf("squats", "lunges", "calf").map { PlannedExercise(it) }),
    WorkoutTemplate("Upper", "Upper", listOf(
        PlannedExercise("pull-ups"), PlannedExercise("dips"),
        PlannedExercise("neutral-grip-pull-ups", List(3) { SetRecord(7) }),
    )),
)

internal fun WorkoutData.addCurrentExercises(): WorkoutData {
    val addedIds = currentExercises.map { it.id }.toSet()
    return copy(
        exercises = exercises + currentExercises.filter { exercises.none { old -> old.id == it.id } },
        workouts = workouts.map { workout ->
            val preset = starterWorkouts.find { it.id == workout.id } ?: return@map workout
            workout.copy(exercises = workout.exercises.map { planned ->
                if (workout.id == "Pull" && planned.exerciseId == "rows" && planned.targets == List(3) { SetRecord(8) })
                    planned.copy(targets = List(3) { SetRecord(12) })
                else planned
            } + preset.exercises.filter { it.exerciseId in addedIds && workout.exercises.none { old -> old.exerciseId == it.exerciseId } })
        },
    )
}
