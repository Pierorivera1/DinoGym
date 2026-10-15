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
    onUpdateExercise: (Exercise, () -> Unit) -> Unit = { _, _ -> },
    onArchiveExercise: (String, () -> Unit) -> Unit = { _, _ -> },
    onRestoreExercise: (String, () -> Unit) -> Unit = { _, _ -> },
    initialTab: Int = 0,
    onPlans: () -> Unit = {},
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
                        0 -> TrainScreen(state, onRoutine, onHistory, onPlans)
                        1 -> ProgressScreen(state.data, onProgress)
                        2 -> LibraryScreen(state, onProgress, onAddExercise, onUpdateExercise,
                            onArchiveExercise, onRestoreExercise)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainScreen(state: WorkoutState, onRoutine: (String) -> Unit, onHistory: () -> Unit, onPlans: () -> Unit) {
    val data = state.data
    val last = data.sessions.maxByOrNull { it.performedAt }
    val next = data.nextWorkout ?: data.workouts.firstOrNull()
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
                            Text(state.draft?.name ?: next?.name ?: "Create a workout", style = MaterialTheme.typography.headlineMedium)
                            Text("${state.draft?.exercises?.size ?: next?.exercises?.count { planned -> data.exercises.any { it.id == planned.exerciseId && !it.archived } } ?: 0} exercises",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(AppIcons.Train, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .65f))
                    }
                    Button(onClick = { if (next != null || state.draft != null) onRoutine(state.draft?.workoutId ?: next?.id.orEmpty()) else onPlans() }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text(if (state.draft != null) "Resume workout" else if (next == null) "Create workout" else "Start workout", Modifier.weight(1f))
                        Icon(AppIcons.Arrow, null)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("${data.sessions.count { it.performedAt >= weekStart }}", "THIS WEEK", Modifier.weight(1f))
                Stat("${data.sessions.size}", "TOTAL SESSIONS", Modifier.weight(1f))
                Stat("${data.exercises.count { !it.archived }}", "EXERCISES", Modifier.weight(1f))
            }
        }
        item {
            SectionLabel(data.activeSplit?.name ?: "Your workouts", subtitle = "Rotating training split")
            TextButton(onClick = onPlans, enabled = !state.saving) { Text("Manage workouts & splits") }
        }
        items(data.workouts, key = { it.id }) { workout ->
            OutlinedCard(onClick = { onRoutine(workout.id) }, enabled = !state.saving) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium)
                        Text("${workout.exercises.size} exercises", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(AppIcons.Arrow, null)
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
internal fun SessionCard(session: Session, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, saving: Boolean = false) {
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
            if (expanded && (onEdit != null || onDelete != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onEdit != null) TextButton(onClick = onEdit, enabled = !saving) { Text("Edit workout") }
                    if (onDelete != null) TextButton(onClick = onDelete, enabled = !saving) { Text("Delete workout") }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: WorkoutState,
    onProgress: (String) -> Unit,
    onAdd: (Exercise, () -> Unit) -> Unit,
    onUpdate: (Exercise, () -> Unit) -> Unit,
    onArchive: (String, () -> Unit) -> Unit,
    onRestore: (String, () -> Unit) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var archivingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = state.data.exercises.find { it.id == editingId }
    val archiving = state.data.exercises.find { it.id == archivingId }
    val exercises = state.data.exercises.filter {
        it.archived == showArchived && it.name.contains(query, ignoreCase = true)
    }
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Heading("LIBRARY", "Exercises")
            FilledTonalButton(onClick = { adding = true }, enabled = !state.saving) {
                Icon(AppIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("New exercise")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active") })
                FilterChip(selected = showArchived, onClick = { showArchived = true }, label = {
                    Text("Archived (${state.data.exercises.count { it.archived }})")
                })
            }
            OutlinedTextField(query, { query = it }, label = { Text("Search exercises") }, singleLine = true)
        }
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (exercises.isEmpty()) item { EmptyMessage("No exercises", if (showArchived)
                "Archived exercises will appear here." else "Add an exercise to build your routine.") }
            items(exercises, key = { it.id }) { exercise ->
                ExerciseRow(exercise,
                    (if (exercise.archived) "Archived · " else "") + exercise.template.replace("{n}", "…"),
                    onClick = { onProgress(exercise.id) }, trailing = {
                        if (exercise.archived) {
                            TextButton(onClick = { onRestore(exercise.id) {} }, enabled = !state.saving) {
                                Text("Restore", modifier = Modifier.semanticsLabel("Restore ${exercise.name}"))
                            }
                        } else {
                            IconButton(onClick = { editingId = exercise.id }, enabled = !state.saving) {
                                Icon(AppIcons.Edit, contentDescription = "Edit ${exercise.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { archivingId = exercise.id }, enabled = !state.saving) {
                                Icon(AppIcons.Archive, contentDescription = "Archive ${exercise.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    })
            }
        }
    }
    if (adding) ExerciseDialog(null, state.saving, onDismiss = { adding = false }, onSave = onAdd)
    if (editing != null) ExerciseDialog(editing, state.saving,
        onDismiss = { editingId = null }, onSave = onUpdate)
    if (archiving != null) ArchiveExerciseDialog(archiving, state.saving,
        onDismiss = { archivingId = null }, onArchive = onArchive)
}

@Composable
internal fun ArchiveExerciseDialog(exercise: Exercise, saving: Boolean, onDismiss: () -> Unit,
    onArchive: (String, () -> Unit) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Archive ${exercise.name}?") },
        text = { Text("This hides the exercise from new workouts. Its saved workouts and progress will remain in history.") },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onArchive(exercise.id, onDismiss) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (saving) "Archiving…" else "Archive")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun RoutineFilter(selected: String, names: List<String> = routines, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("All") + names) { label ->
            FilterChip(selected = selected == label, onClick = { onSelect(label) }, label = { Text(label) })
        }
    }
}

@Composable
internal fun ExerciseRow(
    exercise: Exercise,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = { Icon(AppIcons.Arrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}

@Composable
internal fun ExerciseDialog(existing: Exercise?, saving: Boolean, onDismiss: () -> Unit,
    onSave: (Exercise, () -> Unit) -> Unit) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var template by rememberSaveable(existing?.id) { mutableStateOf(existing?.template ?: "{n} reps") }
    var unit by rememberSaveable(existing?.id) { mutableStateOf(existing?.unit ?: "reps") }
    val validTemplate = template.split("{n}").size == 2 && template.length <= 120
    val valid = name.trim().isNotEmpty() && name.length <= 80 && validTemplate && unit.trim().isNotEmpty() && unit.length <= 20
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = {
        Text(if (existing == null) "New exercise" else "Edit exercise")
    }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true,
                enabled = !saving)
            OutlinedTextField(template, { template = it.take(120) }, label = { Text("Display template") },
                enabled = !saving, isError = !validTemplate,
                supportingText = { Text("Use exactly one {n}, e.g. 3s + {n} pulls + 3s") })
            OutlinedTextField(unit, { unit = it.take(20) }, label = { Text("Metric unit") }, singleLine = true,
                enabled = !saving)
        }
    }, confirmButton = {
        TextButton(enabled = valid && !saving, onClick = {
            onSave(Exercise(existing?.id ?: UUID.randomUUID().toString(), name.trim(),
                template, unit.trim(), existing?.archived ?: false), onDismiss)
        }) { Text(if (saving) "Saving…" else if (existing == null) "Add exercise" else "Save changes") }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
