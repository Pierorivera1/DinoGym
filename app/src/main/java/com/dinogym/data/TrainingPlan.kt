@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.dinogym.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val targets: List<SetRecord> = List(3) { SetRecord(8) },
    val restSeconds: Int = 165,
) {
    init { require(exerciseId.isNotBlank() && targets.size in 1..99 && restSeconds in 0..3600) }
}

@Serializable
data class WorkoutTemplate(val id: String, val name: String, val exercises: List<PlannedExercise>) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 80)
        require(exercises.map { it.exerciseId }.distinct().size == exercises.size)
    }
}

@Serializable
data class TrainingSplit(val id: String, val name: String, val workoutIds: List<String>) {
    init { require(id.isNotBlank() && name.isNotBlank() && name.length <= 80 && workoutIds.isNotEmpty()) }
}

@Serializable
data class DraftSet(val value: String = "8", val weight: String = "", val completed: Boolean = false) {
    fun record(): SetRecord? {
        val reps = value.toIntOrNull()?.takeIf { it in 0..9999 } ?: return null
        val kg = if (weight.isBlank()) null else weight.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..9999.0 } ?: return null
        return SetRecord(reps, kg)
    }
}

@Serializable
data class DraftExercise(
    val exercise: Exercise,
    val sets: List<DraftSet>,
    val restSeconds: Int = 165,
    val notes: String = "",
) {
    init { require(sets.size in 1..99 && restSeconds in 0..3600 && notes.length <= 500) }
}

@Serializable
data class WorkoutDraft(
    @kotlinx.serialization.EncodeDefault val id: String = UUID.randomUUID().toString(),
    val workoutId: String?,
    val name: String,
    @kotlinx.serialization.EncodeDefault val startedAt: Long = System.currentTimeMillis(),
    val exercises: List<DraftExercise>,
    val editing: Boolean = false,
) {
    val completedCount get() = exercises.sumOf { e -> e.sets.count { it.completed } }
    val totalCount get() = exercises.sumOf { it.sets.size }
    fun session(): Session = Session(name, exercises.mapNotNull { e ->
        val records = e.sets.filter { it.completed }.map { requireNotNull(it.record()) }
        if (records.isEmpty()) null else Entry(e.exercise, records, e.notes.trim())
    }, startedAt, id, workoutId)

    companion object {
        fun start(workout: WorkoutTemplate, data: WorkoutData) = WorkoutDraft(
            workoutId = workout.id, name = workout.name, exercises = workout.exercises.mapNotNull { planned ->
                data.exercises.find { it.id == planned.exerciseId && !it.archived }?.let { exercise ->
                    DraftExercise(exercise, planned.targets.map { DraftSet(it.value.toString(), it.weightKg?.toString().orEmpty()) }, planned.restSeconds)
                }
            })
        fun edit(session: Session) = WorkoutDraft(session.id, session.workoutId, session.routine, session.performedAt,
            session.entries.map { e -> DraftExercise(e.exercise, e.records.map {
                DraftSet(it.value.toString(), it.weightKg?.toString().orEmpty(), true)
            }, notes = e.notes) }, editing = true)
    }
}
