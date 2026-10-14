package com.dinogym.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinogym.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal fun dateLabel(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

@Composable
fun HomeScreen(
    state: WorkoutState,
    onRoutine: (String) -> Unit,
    onProgress: (String) -> Unit,
    onHistory: () -> Unit,
    onAddExercise: (Exercise, () -> Unit) -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val labels = listOf("Train", "Progress", "Exercises")
    val icons = listOf(AppIcons.Train, AppIcons.Progress, AppIcons.Library)
    if (!LocalInspectionMode.current) BackHandler(tab != 0) { tab = 0 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], null) }, label = { Text(label) })
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(24.dp))
                    labels.forEachIndexed { index, label ->
                        NavigationRailItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], null) }, label = { Text(label) })
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    when (tab) {
                        0 -> TrainScreen(state.data, onRoutine, onHistory)
                        1 -> ProgressScreen(state.data, onProgress)
                        2 -> LibraryScreen(state, onProgress, onAddExercise)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainScreen(data: WorkoutData, onRoutine: (String) -> Unit, onHistory: () -> Unit) {
    val last = data.sessions.maxByOrNull { it.performedAt }
    val next = routines[(routines.indexOf(last?.routine) + 1) % routines.size]
    val weekStart = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(AppIcons.Train, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Text("dinogym", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Heading("OVERVIEW", "Training") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("UP NEXT", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$next day", style = MaterialTheme.typography.headlineMedium)
                            Text("${data.exercises.count { it.routine == next }} exercises",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(AppIcons.Train, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .65f))
                    }
                    Button(onClick = { onRoutine(next) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text("Start workout", Modifier.weight(1f))
                        Icon(AppIcons.Arrow, null)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("${data.sessions.count { it.performedAt >= weekStart }}", "THIS WEEK", Modifier.weight(1f))
                Stat("${data.sessions.size}", "TOTAL SESSIONS", Modifier.weight(1f))
                Stat("${data.exercises.size}", "EXERCISES", Modifier.weight(1f))
            }
        }
        item { SectionLabel("Your routines", subtitle = "Select a routine") }
        items(routines) { routine ->
            Card(onClick = { onRoutine(routine) }, shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text("0${routines.indexOf(routine) + 1}", Modifier.padding(14.dp),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(routine, style = MaterialTheme.typography.titleMedium)
                        Text(if (routine == "Legs") "Lower uses this routine" else
                            "${data.exercises.count { it.routine == routine }} exercises",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(AppIcons.Arrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Last session", modifier = Modifier.weight(1f))
                TextButton(onClick = onHistory) { Text("View history") }
            }
            if (last == null) EmptyMessage("No sessions yet", "Finish a workout to see it here.")
            else SessionCard(last)
        }
    }
}

@Composable
internal fun Heading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionLabel(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyMessage(title: String, body: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SessionCard(session: Session) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = !expanded }, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${session.routine} day", style = MaterialTheme.typography.titleMedium)
                    Text(dateLabel(session.performedAt), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${session.entries.sumOf { it.sets }} sets", style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) session.entries.forEach { entry ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(entry.exercise.name, style = MaterialTheme.typography.titleSmall)
                Text(entry.display, style = MaterialTheme.typography.bodyMedium)
                if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "Hide details" else "Show details", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LibraryScreen(state: WorkoutState, onProgress: (String) -> Unit, onAdd: (Exercise, () -> Unit) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Heading("LIBRARY", "Exercises")
            FilledTonalButton(onClick = { adding = true }, enabled = !state.saving) {
                Icon(AppIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("New exercise")
            }
            RoutineFilter(filter) { filter = it }
        }
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.data.exercises.filter { filter == "All" || it.routine == filter }, key = { it.id }) { exercise ->
                ExerciseRow(exercise, exercise.template.replace("{n}", "…"), onClick = { onProgress(exercise.id) })
            }
        }
    }
    if (adding) AddExerciseDialog(state.saving, onDismiss = { adding = false }, onAdd = onAdd)
}

@Composable
internal fun RoutineFilter(selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("All") + routines) { label ->
            FilterChip(selected = selected == label, onClick = { onSelect(label) }, label = { Text(label) })
        }
    }
}

@Composable
internal fun ExerciseRow(exercise: Exercise, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text("${exercise.routine} · $subtitle", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AppIcons.Arrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddExerciseDialog(saving: Boolean, onDismiss: () -> Unit, onAdd: (Exercise, () -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var routine by rememberSaveable { mutableStateOf("Pull") }
    var template by rememberSaveable { mutableStateOf("{n} reps") }
    var unit by rememberSaveable { mutableStateOf("reps") }
    val validTemplate = template.split("{n}").size == 2 && template.length <= 120
    val valid = name.trim().isNotEmpty() && name.length <= 80 && validTemplate && unit.trim().isNotEmpty() && unit.length <= 20
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("New exercise") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true)
            RoutineFilter(routine) { if (it != "All") routine = it }
            OutlinedTextField(template, { template = it.take(120) }, label = { Text("Display template") },
                isError = !validTemplate, supportingText = { Text("Use exactly one {n}, e.g. 3s + {n} pulls + 3s") })
            OutlinedTextField(unit, { unit = it.take(20) }, label = { Text("Metric unit") }, singleLine = true)
        }
    }, confirmButton = {
        TextButton(enabled = valid && !saving, onClick = {
            onAdd(Exercise(UUID.randomUUID().toString(), name.trim(), routine, template, unit.trim()), onDismiss)
        }) { Text(if (saving) "Saving…" else "Add exercise") }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
