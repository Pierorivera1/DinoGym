package com.dinogym.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dinogym.data.*
import java.util.UUID

@Composable
fun PlannerScreen(state: WorkoutState, onBack: () -> Unit,
    onSaveWorkout: (WorkoutTemplate, () -> Unit) -> Unit,
    onSaveSplit: (TrainingSplit, () -> Unit) -> Unit, onSelectSplit: (String) -> Unit,
    initialWorkoutId: String? = null,
    onAddExercise: (Exercise, () -> Unit) -> Unit = { _, _ -> },
    onDeleteWorkout: (String, () -> Unit) -> Unit = { _, _ -> }) {
    var editingWorkout by rememberSaveable { mutableStateOf(initialWorkoutId) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSplit by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { if (initialWorkoutId != null) onBack() else { editingWorkout = null; editingSplit = null } }
    if (editingWorkout != null) {
        WorkoutTemplateEditor(state, state.data.workouts.find { it.id == editingWorkout }, close, onSaveWorkout, onAddExercise, onDeleteWorkout)
        return
    }
    if (editingSplit != null) {
        SplitEditor(state, state.data.splits.find { it.id == editingSplit }, close, onSaveSplit)
        return
    }
    DetailPage("Training plans", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().widthIn(max = 760.dp).padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading("PLAN", "Training splits", "Choose the rotating order you want to follow.") }
            item { Button(onClick = { editingSplit = "new" }) { Text("New split") } }
            items(state.data.splits, key = { "split-${it.id}" }) { split ->
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(split.name, style = MaterialTheme.typography.titleMedium)
                        Text(split.workoutIds.joinToString(" → ") { id -> state.data.workouts.find { it.id == id }?.name ?: "Unavailable" })
                        Row {
                            TextButton(onClick = { onSelectSplit(split.id) }, enabled = !state.saving && split.id != state.data.activeSplitId) {
                                Text(if (split.id == state.data.activeSplitId) "Following" else "Follow split")
                            }
                            TextButton(onClick = { editingSplit = split.id }, enabled = !state.saving) { Text("Edit split") }
                        }
                    }
                }
            }
            item { SectionLabel("Workouts") }
            item { Button(onClick = { editingWorkout = "new" }) { Text("New workout") } }
            items(state.data.workouts, key = { "workout-${it.id}" }) { workout ->
                OutlinedCard(onClick = { editingWorkout = workout.id }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium)
                        Text("${workout.exercises.size} exercises · Edit workout")
                        TextButton(enabled = !state.saving, onClick = { deletingId = workout.id }) { Text("Delete workout") }
                    }
                }
            }
        }
    }
    state.data.workouts.find { it.id == deletingId }?.let { workout ->
        DeleteWorkoutDialog(workout, state.saving, { deletingId = null }) {
            onDeleteWorkout(workout.id) { deletingId = null }
        }
    }
}

@Composable
private fun WorkoutTemplateEditor(state: WorkoutState, existing: WorkoutTemplate?, onBack: () -> Unit,
    onSave: (WorkoutTemplate, () -> Unit) -> Unit,
    onAddExercise: (Exercise, () -> Unit) -> Unit,
    onDeleteWorkout: (String, () -> Unit) -> Unit) {
    var id by rememberSaveable { mutableStateOf(existing?.id ?: UUID.randomUUID().toString()) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var encoded by rememberSaveable { mutableStateOf(WorkoutCodec.json.encodeToString(existing?.exercises.orEmpty().mapNotNull { planned ->
        state.data.exercises.find { it.id == planned.exerciseId }?.let { exercise ->
            DraftExercise(exercise, planned.targets.map { DraftSet(if (planned.maxReps) "" else it.value.toString(), it.weightKg?.toString().orEmpty()) }, planned.restSeconds, maxReps = planned.maxReps)
        }
    })) }
    val entries = WorkoutCodec.json.decodeFromString<List<DraftExercise>>(encoded)
    fun change(value: List<DraftExercise>) { encoded = WorkoutCodec.json.encodeToString(value) }
    val latestEntries by rememberUpdatedState(entries)
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val dragPadding = with(density) { 16.dp.toPx() }
    fun moveDragged() {
        val from = latestEntries.indexOfFirst { it.exercise.id == draggingId }
        val item = listState.layoutInfo.visibleItemsInfo.find { it.key == draggingId } ?: return
        val center = dragTop + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            val to = latestEntries.indexOfFirst { it.exercise.id == candidate.key }
            to >= 0 && ((to > from && center > candidate.offset + candidate.size / 2f) ||
                (to < from && center < candidate.offset + candidate.size / 2f))
        } ?: return
        val to = latestEntries.indexOfFirst { it.exercise.id == target.key }
        if (from >= 0) change(latestEntries.toMutableList().apply { add(to, removeAt(from)) })
    }
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val scroll = when {
                pointerY < layout.viewportStartOffset + edge -> -edge / 5
                pointerY > layout.viewportEndOffset - edge -> edge / 5
                else -> 0f
            }
            if (scroll != 0f) {
                listState.scrollBy(scroll)
                moveDragged()
            }
        }
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var restId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var leave by rememberSaveable { mutableStateOf(false) }
    val back = { if (!state.saving) leave = true }
    BackHandler { back() }
    val valid = name.isNotBlank() && entries.all { e -> e.sets.all { it.record() != null || (e.maxReps && it.value.isBlank() && it.weight.isBlank()) } }
    DetailPage(if (existing == null) "New workout" else "Edit workout", back, actions = {
        if (existing != null) TextButton(enabled = !state.saving, onClick = { deleting = true }) { Text("Delete workout") }
    }, bottomBar = {
        Button(enabled = valid && !state.saving, onClick = {
            onSave(WorkoutTemplate(id, name.trim(), entries.map { e ->
                PlannedExercise(e.exercise.id, e.sets.map { if (e.maxReps && it.value.isBlank() && it.weight.isBlank()) SetRecord(0) else requireNotNull(it.record()) }, e.restSeconds, e.maxReps)
            }), onBack)
        }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) { Text("Save workout") }
    }) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding), state = listState,
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Workout name") }, enabled = !state.saving) }
            itemsIndexed(entries, key = { _, e -> e.exercise.id }) { index, entry ->
                val dragging = draggingId == entry.exercise.id
                OutlinedCard(onClick = { expanded = if (expanded == entry.exercise.id) null else entry.exercise.id },
                    modifier = Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer {
                        translationY = if (dragging) dragTop - (listState.layoutInfo.visibleItemsInfo
                            .find { it.key == entry.exercise.id }?.offset ?: 0) else 0f
                        shadowElevation = if (dragging) 8.dp.toPx() else 0f
                    }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.DragHandle, "Reorder ${entry.exercise.name}",
                                Modifier.size(48.dp).semantics {
                                    customActions = if (state.saving) emptyList() else buildList {
                                        if (index > 0) add(CustomAccessibilityAction("Move up") {
                                            change(entries.toMutableList().apply { add(index - 1, removeAt(index)) }); true
                                        })
                                        if (index < entries.lastIndex) add(CustomAccessibilityAction("Move down") {
                                            change(entries.toMutableList().apply { add(index + 1, removeAt(index)) }); true
                                        })
                                    }
                                }.pointerInput(entry.exercise.id, state.saving) {
                                    if (!state.saving) detectDragGesturesAfterLongPress(
                                        onDragStart = { position ->
                                            // Keep every drag target reachable when an exercise editor is taller than the viewport.
                                            expanded = null
                                            listState.layoutInfo.visibleItemsInfo.find { it.key == entry.exercise.id }?.let {
                                                dragTop = it.offset.toFloat()
                                                pointerY = it.offset + dragPadding + position.y
                                                draggingId = entry.exercise.id
                                            }
                                        },
                                        onDragEnd = { draggingId = null },
                                        onDragCancel = { draggingId = null },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragTop += amount.y
                                            pointerY += amount.y
                                            moveDragged()
                                        })
                                }.padding(12.dp))
                            Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                        }
                        val targets = entry.sets.map { if (entry.maxReps) "max reps" else entry.exercise.template.replace("{n}", it.value) }.distinct()
                        Text("${entry.sets.size} × ${targets.singleOrNull() ?: "varied ${entry.exercise.unit}"} · ${formatRestDuration(entry.restSeconds)} rest" + if (entry.exercise.archived) " · Archived" else "")
                        if (expanded == entry.exercise.id) {
                        Row {
                            TextButton(enabled = !state.saving, onClick = { change(entries.filterIndexed { i, _ -> i != index }) }) { Text("Remove") }
                        }
                            SetRows(entry.sets, entry.exercise, !state.saving,
                                onChange = { sets -> change(entries.mapIndexed { i, e -> if (i == index) e.copy(sets = sets) else e }) }, maxReps = entry.maxReps)
                            TextButton(onClick = { restId = entry.exercise.id }) { Text("Rest · ${formatRestDuration(entry.restSeconds)}") }
                        }
                    }
                }
            }
            item { OutlinedButton(enabled = !state.saving, onClick = { picking = true }) { Text("Add exercise") } }
        }
    }
    if (picking) ExercisePicker(state.data.exercises.filter { !it.archived && entries.none { e -> e.exercise.id == it.id } },
        saving = state.saving, onCreate = onAddExercise,
        onDismiss = { picking = false }, onPick = { exercise ->
            change(entries + DraftExercise(exercise, List(3) { DraftSet() })); picking = false; expanded = exercise.id
        })
    entries.find { it.exercise.id == restId }?.let { entry ->
        RestDurationDialog(entry.restSeconds, { restId = null }) { seconds ->
            change(entries.map { if (it.exercise.id == entry.exercise.id) it.copy(restSeconds = seconds) else it })
        }
    }
    if (deleting && existing != null) DeleteWorkoutDialog(existing, state.saving, { deleting = false }) {
        onDeleteWorkout(existing.id, onBack)
    }
    if (leave) DiscardPlanDialog({ leave = false }, onBack)
}

@Composable
private fun SplitEditor(state: WorkoutState, existing: TrainingSplit?, onBack: () -> Unit,
    onSave: (TrainingSplit, () -> Unit) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var ids by rememberSaveable { mutableStateOf(existing?.workoutIds.orEmpty()) }
    var leave by rememberSaveable { mutableStateOf(false) }
    val back = { if (!state.saving) leave = true }
    BackHandler { back() }
    DetailPage(if (existing == null) "New split" else "Edit split", back, bottomBar = {
        Button(enabled = name.isNotBlank() && ids.isNotEmpty() && !state.saving,
            onClick = { onSave(TrainingSplit(id, name.trim(), ids), onBack) },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) { Text("Save split") }
    }) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Split name") }, enabled = !state.saving) }
            item { Text("Workouts repeat in this order. You can include a workout more than once.") }
            itemsIndexed(ids) { index, workoutId ->
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("${index + 1}. ${state.data.workouts.find { it.id == workoutId }?.name.orEmpty()}")
                        Row {
                            TextButton(enabled = index > 0 && !state.saving, onClick = {
                                ids = ids.toMutableList().apply { add(index - 1, removeAt(index)) }
                            }) { Text("Move up") }
                            TextButton(enabled = index < ids.lastIndex && !state.saving, onClick = {
                                ids = ids.toMutableList().apply { add(index + 1, removeAt(index)) }
                            }) { Text("Move down") }
                            TextButton(enabled = !state.saving, onClick = { ids = ids.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
                        }
                    }
                }
            }
            item { SectionLabel("Add a workout") }
            if (state.data.workouts.isEmpty()) item { Text("Create a workout first, then add it to your split.") }
            items(state.data.workouts) { workout ->
                TextButton(enabled = !state.saving, onClick = { ids = ids + workout.id }) { Text("Add ${workout.name}") }
            }
        }
    }
    if (leave) DiscardPlanDialog({ leave = false }, onBack)
}

@Composable
internal fun ExercisePicker(exercises: List<Exercise>, onDismiss: () -> Unit, onPick: (Exercise) -> Unit,
    saving: Boolean, onCreate: (Exercise, () -> Unit) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        ExerciseDialog(null, saving, onDismiss = { creating = false }, onSave = { exercise, close ->
            onCreate(exercise) { close(); onPick(exercise) }
        })
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add exercise") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Search exercises") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val matches = exercises.filter { it.name.contains(query, ignoreCase = true) }
                item { TextButton(onClick = { creating = true }, enabled = !saving) { Text("Create exercise") } }
                if (matches.isEmpty()) item { Text("No matching exercises.") }
                items(matches, key = { it.id }) { exercise ->
                    TextButton(onClick = { onPick(exercise) }) { Text(exercise.name) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun DiscardPlanDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Discard changes?") },
        text = { Text("Your workout plan changes haven't been saved.") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep editing") } })
}

@Composable
private fun DeleteWorkoutDialog(workout: WorkoutTemplate, saving: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Delete ${workout.name}?") },
        text = { Text("Remove this workout from your plans and splits? Empty splits will also be removed. Saved workout history will be kept.") },
        confirmButton = { TextButton(enabled = !saving, onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
