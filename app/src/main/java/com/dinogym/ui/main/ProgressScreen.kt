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
import androidx.compose.ui.unit.dp
import com.dinogym.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun ProgressScreen(data: WorkoutData, onProgress: (String, String) -> Unit) {
    var workoutId by rememberSaveable { mutableStateOf<String?>(null) }
    val ordered = (data.activeSplit?.workoutIds.orEmpty().mapNotNull { id -> data.workouts.find { it.id == id } } + data.workouts).distinctBy { it.id }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("WORKOUT HISTORY", "Your progress", "Choose a workout, then an exercise to see your weekly progress.") }
        items(ordered, key = { it.id }) { workout ->
            OutlinedCard(onClick = { workoutId = if (workoutId == workout.id) null else workout.id }) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(workout.name, style = MaterialTheme.typography.titleLarge)
                    Text("${workout.exercises.size} exercises · ${if (workoutId == workout.id) "Hide" else "View progress"}")
                }
            }
            if (workoutId == workout.id) {
                val ids = (workout.exercises.map { it.exerciseId } + data.sessions.filter { it.belongsTo(workout) }
                    .flatMap { it.entries.map { entry -> entry.exercise.id } }).distinct()
                ids.forEach { id ->
                    data.progressExercises.find { it.id == id }?.let { exercise ->
                        ExerciseRow(exercise, data.history(id).lastOrNull { it.first.belongsTo(workout) }?.second?.let { "Latest: ${it.display}" } ?: "No sessions yet", onClick = { onProgress(id, workout.id) })
                    }
                }
            }
        }
        if (ordered.isEmpty()) item { EmptyMessage("No workouts yet", "Create a workout to organize exercise progress.") }
    }
}

@Composable
fun ExerciseProgressScreen(
    exerciseId: String,
    data: WorkoutData,
    onBack: () -> Unit,
    saving: Boolean = false,
    workoutId: String? = null,
    onUpdateExercise: (Exercise, () -> Unit) -> Unit = { _, _ -> },
    onArchiveExercise: (String, () -> Unit) -> Unit = { _, _ -> },
    onRestoreExercise: (String, () -> Unit) -> Unit = { _, _ -> },
) {
    val exercise = data.progressExercises.find { it.id == exerciseId }
    val editable = data.exercises.find { it.id == exerciseId }
    var editing by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var archiving by rememberSaveable(exerciseId) { mutableStateOf(false) }
    val workout = data.workouts.find { it.id == workoutId }
    val history = data.history(exerciseId).filter { workout == null || it.first.belongsTo(workout) }
    DetailPage("Exercise progress", { if (!saving) onBack() }) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { Heading(workout?.name ?: "EXERCISE", exercise?.name ?: "Exercise unavailable",
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
                            Text("Weekly progress", style = MaterialTheme.typography.titleMedium)
                            val recent = history
                            val weekly = weeklyProgress(history)
                            Text("0–${recent.maxOf { it.second.value }} ${exercise?.unit.orEmpty()}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ValueChart(weekly, exercise?.unit.orEmpty())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(dateLabel(recent.first().first.performedAt), style = MaterialTheme.typography.labelMedium)
                                if (recent.size > 1) Text(dateLabel(recent.last().first.performedAt), style = MaterialTheme.typography.labelMedium)
                            }
                            Text("Best set each week · weeks without logs have no dot. All sets shown below.", style = MaterialTheme.typography.bodySmall,
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
private fun ValueChart(points: List<WeeklyProgress>, unit: String) {
    val primary = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(180.dp).semanticsLabel(
        "Weekly best set in $unit: ${points.joinToString { "${it.week}: ${it.value}" }}",
    )) {
        val max = points.maxOf { it.value }.coerceAtLeast(1)
        val inset = 6.dp.toPx()
        val span = ChronoUnit.WEEKS.between(points.first().week, points.last().week).coerceAtLeast(1)
        val positions = points.map {
            Offset(inset + (size.width - 2 * inset) * ChronoUnit.WEEKS.between(points.first().week, it.week).toFloat() / span,
                size.height - inset - (size.height - 2 * inset) * it.value / max)
        }
        drawLine(baseline, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
        positions.zipWithNext().forEach { (start, end) -> drawLine(primary, start, end, 2.dp.toPx()) }
        positions.forEach { drawCircle(primary, 4.dp.toPx(), it) }
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
