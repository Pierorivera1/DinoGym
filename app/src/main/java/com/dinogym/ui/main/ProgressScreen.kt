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
        item { RoutineFilter(filter) { filter = it } }
        if (data.sessions.isEmpty()) item {
            EmptyMessage("No sessions yet", "Log a workout and your exercise history will appear here.")
        }
        items(data.exercises.filter { filter == "All" || it.routine == filter }, key = { it.id }) { exercise ->
            val history = data.history(exercise.id)
            ExerciseRow(exercise, history.lastOrNull()?.second?.let { "Latest: ${it.display}" } ?: "No sessions yet",
                onClick = { onProgress(exercise.id) })
        }
    }
}

@Composable
fun ExerciseProgressScreen(exerciseId: String, data: WorkoutData, onBack: () -> Unit) {
    val exercise = data.exercises.find { it.id == exerciseId }
    val history = data.history(exerciseId)
    DetailPage("Exercise progress", onBack) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item { Heading(exercise?.routine?.uppercase() ?: "EXERCISE", exercise?.name ?: "Exercise unavailable",
                exercise?.template?.replace("{n}", "…") ?: "") }
            if (history.isEmpty()) item {
                EmptyMessage("No entries yet", "Log this exercise during a workout to see its progress.")
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Stat("${history.last().second.value}", "LATEST ${exercise?.unit?.uppercase()}", Modifier.weight(1f))
                        Stat("${history.maxOf { it.second.value }}", "BEST ${exercise?.unit?.uppercase()}", Modifier.weight(1f))
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
                            Text("Value per session · sets shown below", style = MaterialTheme.typography.bodySmall,
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
fun HistoryScreen(data: WorkoutData, onBack: () -> Unit) {
    DetailPage("Workout history", onBack) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading("SESSIONS", "Workout history", "${data.sessions.size} workouts logged on this device.") }
            if (data.sessions.isEmpty()) item { EmptyMessage("No sessions yet", "Finish your first workout to start your history.") }
            items(data.sessions.sortedByDescending { it.performedAt }, key = { it.id }) { SessionCard(it) }
        }
    }
}
