package com.dinogym.data

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// shortcut: one local JSON snapshot, migrate to Room before adding edits or cloud sync.
class WorkoutRepository(directory: File) {
    private val file = AtomicFile(File(directory, "workouts.json"))
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): WorkoutData = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) WorkoutData()
        else file.openRead().bufferedReader().use { json.decodeFromString<WorkoutData>(it.readText()) }
    }

    suspend fun save(data: WorkoutData) = withContext(Dispatchers.IO) {
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
