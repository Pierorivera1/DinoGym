package com.dinogym.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.dinogym.data.*
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProgressScreen(data: WorkoutData, onProgress: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("EXERCISE HISTORY", "Your progress") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("${data.sessions.size}", "SESSIONS", Modifier.weight(1f))
                Stat("${data.sessions.sumOf { session -> session.entries.sumOf { it.sets } }}", "SETS LOGGED", Modifier.weight(1f))
            }
        }
        item { RoutineFilter(filter, data.workouts.map { it.name }) { filter = it } }
        if (data.sessions.isEmpty()) item {
            EmptyMessage("No sessions yet", "Log a workout and your exercise history will appear here.")
        }
        items(data.progressExercises.filter { filter == "All" || data.workouts.any { w -> w.name == filter && w.exercises.any { e -> e.exerciseId == it.id } } }, key = { it.id }) { exercise ->
            val history = data.history(exercise.id)
            ExerciseRow(exercise, history.lastOrNull()?.second?.let { "Latest: ${it.display}" } ?: "No sessions yet",
                onClick = { onProgress(exercise.id) })
        }
    }
}

@Composable
fun ExerciseProgressScreen(
    exerciseId: String,
    data: WorkoutData,
    onBack: () -> Unit,
    saving: Boolean = false,
    onUpdateExercise: (Exercise, () -> Unit) -> Unit = { _, _ -> },
    onArchiveExercise: (String, () -> Unit) -> Unit = { _, _ -> },
    onRestoreExercise: (String, () -> Unit) -> Unit = { _, _ -> },
) {
    val exercise = data.progressExercises.find { it.id == exerciseId }
    val editable = data.exercises.find { it.id == exerciseId }
    var editing by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var archiving by rememberSaveable(exerciseId) { mutableStateOf(false) }
    val history = data.history(exerciseId)
    DetailPage("Exercise progress", { if (!saving) onBack() }) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { Heading("EXERCISE", exercise?.name ?: "Exercise unavailable",
                exercise?.template?.replace("{n}", "…") ?: "") }
            if (editable != null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = true }, enabled = !saving) { Text("Edit exercise") }
                    if (editable.archived) {
                        TextButton(onClick = { onRestoreExercise(editable.id) {} }, enabled = !saving) { Text("Restore exercise") }
                    } else {
                        TextButton(onClick = { archiving = true }, enabled = !saving) { Text("Archive exercise") }
                    }
                }
            }
            if (history.isEmpty()) item {
                EmptyMessage("No entries yet", "Log this exercise during a workout to see its progress.")
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Stat("${history.last().second.value}", "LATEST BEST SET", Modifier.weight(1f))
                        Stat("${history.maxOf { it.second.value }}", "BEST SET", Modifier.weight(1f))
                        Stat("${history.size}", "ENTRIES", Modifier.weight(1f))
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
                            val recent = history.takeLast(8)
                            Text("0–${recent.maxOf { it.second.value }} ${exercise?.unit.orEmpty()}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ValueChart(recent.map { it.second.value }, exercise?.unit.orEmpty())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(dateLabel(recent.first().first.performedAt), style = MaterialTheme.typography.labelMedium)
                                if (recent.size > 1) Text(dateLabel(recent.last().first.performedAt), style = MaterialTheme.typography.labelMedium)
                            }
                            Text("Best set value per session · all sets shown below", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { SectionLabel("Session history") }
                items(history.asReversed(), key = { it.first.id }) { (session, entry) ->
                    OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(dateLabel(session.performedAt), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.display, style = MaterialTheme.typography.titleLarge)
                            if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    if (editing && editable != null) ExerciseDialog(editable, saving,
        onDismiss = { editing = false }, onSave = onUpdateExercise)
    if (archiving && editable != null) ArchiveExerciseDialog(editable, saving,
        onDismiss = { archiving = false }, onArchive = onArchiveExercise)
}

@Composable
private fun ValueChart(values: List<Int>, unit: String) {
    val primary = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(160.dp).semanticsLabel(
        "Recent values in $unit, oldest first: ${values.joinToString()}. Maximum ${values.maxOrNull() ?: 0}.",
    )) {
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val gap = size.width / values.size
        val barWidth = minOf(gap * .5f, 32.dp.toPx())
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val height = (size.height - 12.dp.toPx()) * value / max
            drawRoundRect(primary.copy(alpha = if (index == values.lastIndex) 1f else .35f),
                topLeft = Offset(gap * index + (gap - barWidth) / 2, size.height - height),
                size = Size(barWidth, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
        }
    }
}

@Composable
fun HistoryScreen(
    data: WorkoutData,
    onBack: () -> Unit,
    onEdit: ((String) -> Unit)? = null,
    onDelete: ((String, () -> Unit) -> Unit)? = null,
    saving: Boolean = false,
) {
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    val sessions = data.sessions.filter {
        selectedDay == null || Instant.ofEpochMilli(it.performedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() == selectedDay
    }.sortedByDescending { it.performedAt }
    DetailPage("Workout history", onBack) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading("SESSIONS", "Workout history", "${data.sessions.size} workouts logged on this device.") }
            if (data.sessions.isNotEmpty()) item { HistoryCalendar(data.sessions, selectedDay) { selectedDay = it } }
            if (data.sessions.isEmpty()) item { EmptyMessage("No sessions yet", "Finish your first workout to start your history.") }
            else if (sessions.isEmpty()) item { EmptyMessage("No workouts on this day", "Choose a marked day or show all workouts.") }
            items(sessions, key = { it.id }) { session ->
                SessionCard(session, onEdit = onEdit?.let { { it(session.id) } },
                    onDelete = onDelete?.let { { deletingId = session.id } }, saving = saving)
            }
        }
    }
    val deleting = data.sessions.find { it.id == deletingId }
    if (deleting != null && onDelete != null) AlertDialog(
        onDismissRequest = { if (!saving) deletingId = null },
        title = { Text("Delete workout?") },
        text = { Text("Delete the ${deleting.routine} workout from ${dateLabel(deleting.performedAt)}? This cannot be undone.") },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onDelete(deleting.id) { deletingId = null } }) {
                Text(if (saving) "Deleting…" else "Delete")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { deletingId = null }) { Text("Cancel") } },
    )
}
