package com.dinogym.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.dinogym.data.*
import com.dinogym.timer.RestTimerService
import android.os.SystemClock
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailPage(title: String, onBack: () -> Unit, bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, "Back") }
        }, actions = actions, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }, bottomBar = bottomBar) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { content(padding) }
    }
}

@Composable
fun WorkoutScreen(
    routine: String, state: WorkoutState, onBack: () -> Unit, onSave: (Session) -> Unit,
    session: Session? = null, onDraft: (WorkoutDraft) -> Unit = {},
    onDiscard: (() -> Unit)? = null, onFinish: ((Session, Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val initial = remember(routine, session?.id) {
        state.draft?.takeIf { if (session != null) it.id == session.id else it.workoutId == routine }
            ?: session?.let(WorkoutDraft::edit)
            ?: WorkoutDraft.start(state.data.workouts.find { it.id == routine }
                ?: WorkoutTemplate(routine, routine, emptyList()), state.data)
    }
    var encoded by rememberSaveable(routine, session?.id) { mutableStateOf(WorkoutCodec.json.encodeToString(initial)) }
    val draft = WorkoutCodec.json.decodeFromString<WorkoutDraft>(encoded)
    fun change(value: WorkoutDraft) {
        encoded = WorkoutCodec.json.encodeToString(value)
        onDraft(value)
    }
    LaunchedEffect(initial.id) { onDraft(draft) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var updateTargets by rememberSaveable { mutableStateOf(false) }
    var timerDialog by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val selected = draft.exercises.find { it.exercise.id == selectedId }
    val nextExercise = selected?.let { current ->
        val index = draft.exercises.indexOf(current)
        (draft.exercises.drop(index + 1) + draft.exercises.take(index))
            .firstOrNull { e -> e.sets.any { !it.completed } }
    }
    fun updateExercise(entry: DraftExercise) = change(draft.copy(exercises = draft.exercises.map {
        if (it.exercise.id == entry.exercise.id) entry else it
    }))
    fun startRest(seconds: Int) {
        if (seconds > 0 && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = context.getSharedPreferences("rest-permission", 0)
            if (!prefs.getBoolean("asked", false)) {
                prefs.edit().putBoolean("asked", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        RestTimerService.start(context, seconds)
    }
    fun log(entry: DraftExercise, index: Int) {
        val set = entry.sets[index]
        if (set.record() == null) return
        updateExercise(entry.copy(sets = entry.sets.mapIndexed { i, old -> if (i == index) old.copy(completed = !old.completed) else old }))
        if (!set.completed && !draft.editing) startRest(entry.restSeconds)
    }
    val back = { if (!state.saving) { if (selectedId != null) selectedId = null else onBack() } }
    if (!LocalInspectionMode.current) BackHandler { back() }
    val timer by RestTimerService.state.collectAsState()
    var remaining by remember { mutableLongStateOf(timer.remaining(SystemClock.elapsedRealtime())) }
    LaunchedEffect(timer) {
        do { remaining = timer.remaining(SystemClock.elapsedRealtime()); delay(250) }
        while (timer.deadline > 0 && remaining > 0)
    }
    DetailPage(selected?.exercise?.name ?: draft.name, back, actions = {
        Box {
            IconButton(onClick = { menu = true }, enabled = !state.saving) { Icon(AppIcons.More, "Workout options") }
            DropdownMenu(menu, { menu = false }) {
                if (selected != null) DropdownMenuItem(text = { Text("Remove exercise from session") }, onClick = {
                    change(draft.copy(exercises = draft.exercises.filterNot { it.exercise.id == selected.exercise.id }))
                    selectedId = null; menu = false
                })
                DropdownMenuItem(text = { Text("Discard workout") }, onClick = { menu = false; discard = true })
            }
        }
    }, bottomBar = {
        Surface {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.active) Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rest ${((remaining + 999) / 1000) / 60}:${(((remaining + 999) / 1000) % 60).toString().padStart(2, '0')}", Modifier.weight(1f))
                        TextButton(onClick = { RestTimerService.command(context, if (timer.deadline > 0) "pause" else "resume") }) {
                            Text(if (timer.deadline > 0) "Pause" else "Resume")
                        }
                        TextButton(onClick = { RestTimerService.stop(context) }) { Text("Skip rest") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { RestTimerService.command(context, "subtract15") }) { Text("−15s") }
                        OutlinedButton(onClick = { RestTimerService.command(context, "add15") }) { Text("+15s") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { timerDialog = true }, enabled = !state.saving,
                        modifier = Modifier.semanticsLabel("Rest timer")) { Icon(AppIcons.Timer, null) }
                    if (selected != null && !draft.editing) {
                        val next = selected.sets.indexOfFirst { !it.completed }
                        Button(onClick = {
                            if (next >= 0) log(selected, next)
                            else if (nextExercise != null) selectedId = nextExercise.exercise.id
                            else finishing = true
                        }, enabled = !state.saving && (next < 0 || selected.sets[next].record() != null),
                            modifier = Modifier.weight(1f)) {
                            Text(if (next >= 0) "Log set" else if (nextExercise != null) "Next exercise" else "Finish workout")
                        }
                        TextButton(onClick = { selectedId = null }) { Text("Overview") }
                    } else Button(onClick = { finishing = true },
                        enabled = !state.saving && draft.completedCount > 0 && draft.exercises.all { e -> e.sets.filter { it.completed }.all { it.record() != null } },
                        modifier = Modifier.weight(1f)) {
                        Text(if (state.saving) "Saving…" else if (draft.editing) "Save changes" else "Finish workout")
                    }
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (selected == null) {
                item { SectionLabel(if (draft.editing) "Edit session" else "Exercises",
                    subtitle = "${draft.completedCount}/${draft.totalCount} sets logged") }
                if (draft.exercises.isEmpty()) item { EmptyMessage("No exercises yet", "Add an active exercise to this workout.") }
                if (draft.completedCount == 0) item { Text("Log at least one set before finishing.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(draft.exercises, key = { _, e -> e.exercise.id }) { index, entry ->
                    Surface(onClick = { selectedId = entry.exercise.id }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelLarge)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                                val values = entry.sets.map { it.value }.distinct()
                                Text("${entry.sets.size} × ${if (values.size == 1) entry.exercise.template.replace("{n}", values.first()) else "varied ${entry.exercise.unit}"} · ${formatRestDuration(entry.restSeconds)} rest",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${entry.sets.count { it.completed }}/${entry.sets.size}",
                                modifier = Modifier.padding(horizontal = 8.dp).semanticsLabel("${entry.sets.count { it.completed }} of ${entry.sets.size} sets logged"),
                                style = MaterialTheme.typography.labelMedium)
                            Icon(AppIcons.Arrow, "Edit ${entry.exercise.name} sets")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item { TextButton(onClick = { picking = true }, enabled = !state.saving) { Text("Add exercise") } }
            } else {
                val previous = state.data.history(selected.exercise.id).lastOrNull {
                    it.first.id != draft.id && it.first.performedAt < draft.startedAt
                }
                if (previous != null) item {
                    Text("Last time · ${dateLabel(previous.first.performedAt)}", style = MaterialTheme.typography.labelMedium)
                    Text(previous.second.display, style = MaterialTheme.typography.bodyMedium)
                    if (previous.second.notes.isNotBlank()) Text(previous.second.notes,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetRows(selected.sets, selected.exercise, !state.saving,
                            onChange = { updateExercise(selected.copy(sets = it)) }, onLog = { log(selected, it) })
                        if (selected.sets.any { it.record() == null }) Text("Enter 0–9999 ${selected.exercise.unit}; weight must be a non-negative number in kg.", color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(selected.notes, { updateExercise(selected.copy(notes = it.take(500))) },
                            label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                        TextButton(onClick = { timerDialog = true }) { Text("Rest · ${formatRestDuration(selected.restSeconds)}") }
                    }
                }
            }
        }
    }
    if (timerDialog) {
        RestDurationDialog(selected?.restSeconds ?: draft.exercises.firstOrNull()?.restSeconds ?: 165,
            onDismiss = { timerDialog = false }, onStart = { startRest(it) },
            startLabel = if (timer.active) "Restart" else "Start") { seconds ->
            if (selected != null) updateExercise(selected.copy(restSeconds = seconds))
            else change(draft.copy(exercises = draft.exercises.map { it.copy(restSeconds = seconds) }))
        }
    }
    if (picking) ExercisePicker(state.data.exercises.filter { !it.archived && draft.exercises.none { e -> e.exercise.id == it.id } },
        { picking = false }, { exercise ->
            change(draft.copy(exercises = draft.exercises + DraftExercise(exercise, List(3) { DraftSet() })))
            picking = false; selectedId = exercise.id
        })
    if (finishing) AlertDialog(onDismissRequest = { if (!state.saving) finishing = false },
        title = { Text(if (draft.editing) "Save session changes?" else "Finish workout?") }, text = {
            Column {
                Text(if (draft.completedCount < draft.totalCount) "Only ${draft.completedCount} of ${draft.totalCount} sets are logged. Unfinished sets will not be saved."
                    else "${draft.completedCount} sets will be saved.")
                if (!draft.editing && state.data.workouts.any { it.id == draft.workoutId }) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(updateTargets, { updateTargets = it }, enabled = !state.saving, modifier = Modifier.semanticsLabel("Update workout targets"))
                    Text("Update workout targets")
                }
            }
        }, confirmButton = {
            TextButton(enabled = !state.saving, onClick = {
                val completed = draft.session()
                if (onFinish != null) onFinish(completed, updateTargets) else onSave(completed)
            }) { Text("Save session") }
        }, dismissButton = { TextButton(enabled = !state.saving, onClick = { finishing = false }) { Text("Keep training") } })
    if (discard) AlertDialog(onDismissRequest = { if (!state.saving) discard = false }, title = { Text("Discard workout?") },
        text = { Text("Your unsaved session changes will be removed.") }, confirmButton = {
            TextButton(enabled = !state.saving, onClick = { RestTimerService.stop(context); if (onDiscard != null) onDiscard() else onBack() }) { Text("Discard changes") }
        }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep training") } })
}
