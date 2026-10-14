package com.dinogym.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dinogym.data.*

private data class Draft(val value: String = "", val sets: String = "2", val notes: String = "", val included: Boolean = true) {
    val valid get() = value.toIntOrNull() in 0..9999 && sets.toIntOrNull() in 1..99
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailPage(title: String, onBack: () -> Unit, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, "Back") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }, bottomBar = bottomBar) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { content(padding) }
    }
}

@Composable
fun WorkoutScreen(routine: String, state: WorkoutState, onBack: () -> Unit, onSave: (Session) -> Unit) {
    val exercises = state.data.exercises.filter { it.routine == routine }
    var drafts by rememberSaveable(stateSaver = listSaver(
        save = { items: List<Draft> -> items.flatMap { listOf(it.value, it.sets, it.notes, it.included.toString()) } },
        restore = { items -> items.chunked(4).map { Draft(it[0], it[1], it[2], it[3].toBoolean()) } },
    )) { mutableStateOf(exercises.map { Draft() }) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val dirty = drafts.any { it.value.isNotBlank() || it.notes.isNotBlank() || it.sets != "2" || !it.included }
    val back = { if (!state.saving) { if (dirty) discard = true else onBack() } }
    if (!LocalInspectionMode.current) BackHandler { back() }
    val selected = drafts.filter { it.included }
    val valid = selected.isNotEmpty() && selected.all { it.valid }
    val last = state.data.sessions.filter { it.routine == routine }.maxByOrNull { it.performedAt }
    DetailPage("$routine workout", back, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Button(enabled = valid && !state.saving, modifier = Modifier.widthIn(max = 712.dp).fillMaxWidth().heightIn(min = 52.dp),
                    onClick = {
                        onSave(Session(routine, exercises.mapIndexedNotNull { index, exercise ->
                            val draft = drafts[index]
                            if (draft.included) Entry(exercise, draft.value.toInt(), draft.sets.toInt(), draft.notes.trim()) else null
                        }))
                    }) { Text(if (state.saving) "Saving…" else "Finish workout") }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Heading("WORKOUT", "$routine day", if (last == null) "No previous session"
                    else "Previous session · ${dateLabel(last.performedAt)}")
            }
            item {
                Text("One value for all sets. Uncheck an exercise to skip it.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (exercises.isEmpty()) item { EmptyMessage("No exercises yet", "Add an exercise to $routine from the Exercises tab.") }
            itemsIndexed(exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                val draft = drafts[index]
                val previous = state.data.history(exercise.id).lastOrNull()?.second
                fun update(value: Draft) { drafts = drafts.mapIndexed { i, old -> if (i == index) value else old } }
                OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text(previous?.let { "Last: ${it.display}" } ?: "No previous session",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = draft.included, enabled = !state.saving,
                                onCheckedChange = { update(draft.copy(included = it)) },
                                modifier = Modifier.semanticsLabel("Log ${exercise.name}"))
                        }
                        if (draft.included) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(draft.value, { update(draft.copy(value = it.take(4))) },
                                    modifier = Modifier.weight(1f), label = { Text(exercise.unit) }, singleLine = true,
                                    enabled = !state.saving, isError = draft.value.isNotEmpty() && draft.value.toIntOrNull() !in 0..9999,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                OutlinedTextField(draft.sets, { update(draft.copy(sets = it.take(2))) },
                                    modifier = Modifier.weight(1f), label = { Text("Sets") }, singleLine = true,
                                    enabled = !state.saving, isError = draft.sets.toIntOrNull() !in 1..99,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            if (draft.valid) Text(exercise.display(draft.value.toInt()) + " × " + draft.sets,
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(draft.notes, { update(draft.copy(notes = it.take(500))) },
                                label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(),
                                enabled = !state.saving, maxLines = 4)
                        }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Leave this workout?") },
        text = { Text("This session hasn't been saved yet.") }, confirmButton = {
            TextButton(onClick = onBack) { Text("Discard workout") }
        }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Continue workout") } })
}
