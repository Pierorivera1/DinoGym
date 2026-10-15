package com.dinogym.data

import kotlinx.serialization.json.*

object WorkoutCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(text: String): WorkoutData {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.int ?: 1
        require(version in 1..2) { "Unsupported workout file version" }
        if (version == 2) {
            val data = json.decodeFromString<WorkoutData>(text)
            return if (root["exercisePresetVersion"] == null) data.addCurrentExercises() else data
        }
        val sourceExercises = root["exercises"]?.jsonArray
        val exercises = sourceExercises?.map { json.decodeFromJsonElement<Exercise>(it) } ?: (starterExercises + listOf(
            starterExercises.first { it.id == "pull-ups" }.copy(id = "upper-pull"),
            starterExercises.first { it.id == "dips" }.copy(id = "upper-dips")))
        val sessionObjects = root["sessions"]?.jsonArray.orEmpty()
        val aliases = mutableMapOf<String, String>()
        listOf("upper-pull" to "pull-ups", "upper-dips" to "dips").forEach { (oldId, id) ->
            val old = exercises.find { it.id == oldId }
            val canonical = exercises.find { it.id == id }
            val together = sessionObjects.any { session ->
                val ids = session.jsonObject.getValue("entries").jsonArray.map {
                    it.jsonObject.getValue("exercise").jsonObject.getValue("id").jsonPrimitive.content
                }
                oldId in ids && id in ids
            }
            if (old != null && canonical != null && old.copy(id = id) == canonical && !together) aliases[oldId] = id
        }
        val sessions = sessionObjects.map { element ->
            val obj = element.jsonObject
            val entries = obj.getValue("entries").jsonArray.map { item ->
                val entry = item.jsonObject
                val exercise = json.decodeFromJsonElement<Exercise>(entry.getValue("exercise"))
                Entry(exercise.copy(id = aliases[exercise.id] ?: exercise.id),
                    entry.getValue("value").jsonPrimitive.int, entry.getValue("sets").jsonPrimitive.int,
                    entry["notes"]?.jsonPrimitive?.content.orEmpty())
            }
            val base = Session(obj.getValue("routine").jsonPrimitive.content, entries)
            base.copy(id = obj["id"]?.jsonPrimitive?.content ?: base.id,
                performedAt = obj["performedAt"]?.jsonPrimitive?.long ?: base.performedAt,
                workoutId = base.routine)
        }
        val workouts = if (sourceExercises == null) starterWorkouts else routines.map { name ->
            val ids = sourceExercises.filter { it.jsonObject["routine"]?.jsonPrimitive?.content == name }
                .map { it.jsonObject.getValue("id").jsonPrimitive.content }
                .map { aliases[it] ?: it }.distinct()
            WorkoutTemplate(name, name, ids.map { id ->
                val recent = sessions.filter { it.routine == name }.maxByOrNull { it.performedAt }
                    ?.entries?.find { it.exercise.id == id }
                PlannedExercise(id, recent?.records ?: List(3) { SetRecord(8) })
            })
        }
        val lastName = sessions.maxByOrNull { it.performedAt }?.routine
        return WorkoutData(exercises.filterNot { it.id in aliases }, sessions, workouts,
            nextWorkoutIndex = (routines.indexOf(lastName) + 1) % routines.size).addCurrentExercises()
    }
}
