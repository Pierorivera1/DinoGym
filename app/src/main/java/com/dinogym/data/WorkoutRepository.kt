package com.dinogym.data

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File

// shortcut: one local JSON snapshot, revisit storage if data size or sync requires incremental writes.
class WorkoutRepository(directory: File) {
    private val file = AtomicFile(File(directory, "workouts.json"))
    private val json = WorkoutCodec.json
    private val directory = directory

    suspend fun load(): WorkoutData = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) WorkoutData()
        else file.openRead().bufferedReader().use { WorkoutCodec.decode(it.readText()) }
    }

    private val draftFile = AtomicFile(File(directory, "active-workout.json"))

    suspend fun loadDraft(): WorkoutDraft? = withContext(Dispatchers.IO) {
        if (!draftFile.baseFile.exists() && !File(draftFile.baseFile.path + ".bak").exists()) null
        else draftFile.openRead().bufferedReader().use { json.decodeFromString<WorkoutDraft>(it.readText()) }
    }

    suspend fun saveDraft(draft: WorkoutDraft?) = withContext(Dispatchers.IO) {
        if (draft == null) {
            draftFile.delete()
            check(!draftFile.baseFile.exists() && !File(draftFile.baseFile.path + ".bak").exists()) {
                "Couldn't remove the active workout draft"
            }
        }
        else {
            val bytes = json.encodeToString(draft).toByteArray(Charsets.UTF_8)
            val stream = draftFile.startWrite()
            try { stream.write(bytes); draftFile.finishWrite(stream) }
            catch (error: Exception) { draftFile.failWrite(stream); throw error }
        }
    }

    suspend fun save(data: WorkoutData) = withContext(Dispatchers.IO) {
        if (file.baseFile.exists() && !File(directory, "workouts-v1.backup.json").exists()) {
            val original = file.openRead().use { it.readBytes() }
            val root = json.parseToJsonElement(original.toString(Charsets.UTF_8)).jsonObject
            if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != 2) {
                val backup = AtomicFile(File(directory, "workouts-v1.backup.json"))
                val stream = backup.startWrite()
                try { stream.write(original); backup.finishWrite(stream) }
                catch (error: Exception) { backup.failWrite(stream); throw error }
            }
        }
        val bytes = json.encodeToString(data).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
