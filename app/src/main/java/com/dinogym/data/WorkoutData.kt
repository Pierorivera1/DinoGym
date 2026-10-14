package com.dinogym.data

import kotlinx.serialization.Serializable
import java.util.UUID

val routines = listOf("Pull", "Push", "Legs", "Upper")

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val routine: String,
    val template: String = "{n} reps",
    val unit: String = "reps",
) {
    init {
        require(name.isNotBlank() && name.length <= 80)
        require(routine in routines)
        require(template.length <= 120 && template.split("{n}").size == 2)
        require(unit.isNotBlank() && unit.length <= 20)
    }
    fun display(value: Int) = template.replace("{n}", value.toString())
}

@Serializable
data class Entry(val exercise: Exercise, val value: Int, val sets: Int, val notes: String = "") {
    init { require(value in 0..9999 && sets in 1..99 && notes.length <= 500) }
    val display get() = "${exercise.display(value)} × $sets"
}

@Serializable
data class Session(
    val routine: String,
    val entries: List<Entry>,
    val performedAt: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
) {
    init {
        require(routine in routines && entries.isNotEmpty())
        require(entries.map { it.exercise.id }.distinct().size == entries.size)
    }
}

@Serializable
data class WorkoutData(val exercises: List<Exercise> = starterExercises, val sessions: List<Session> = emptyList()) {
    fun history(exerciseId: String) = sessions.sortedBy { it.performedAt }
        .mapNotNull { session -> session.entries.find { it.exercise.id == exerciseId }?.let { session to it } }
}

val starterExercises = listOf(
    Exercise("pull-ups", "Pull-ups", "Pull"),
    Exercise("front-pulls", "Front holds + pulls", "Pull", "3s + {n} pulls + 3s", "pulls"),
    Exercise("rows", "Australian rows", "Pull"),
    Exercise("dips", "Dips", "Push"),
    Exercise("push-ups", "Push-ups", "Push"),
    Exercise("pike", "Pike push-ups", "Push"),
    Exercise("squats", "Squats", "Legs"),
    Exercise("lunges", "Lunges", "Legs"),
    Exercise("calf", "Calf raises", "Legs"),
    Exercise("upper-pull", "Pull-ups", "Upper"),
    Exercise("upper-dips", "Dips", "Upper"),
)
