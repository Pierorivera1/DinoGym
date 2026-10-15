package com.dinogym.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinogym.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainScreen(state: WorkoutState, onRoutine: (String) -> Unit, onHistory: () -> Unit,
    onPlans: () -> Unit, onProgress: (String) -> Unit, onEditWorkout: (String) -> Unit) {
    val data = state.data
    val next = data.nextWorkout ?: data.workouts.firstOrNull()
    val days = data.activeSplit?.workoutIds?.mapNotNull { id -> data.workouts.find { it.id == id } } ?: data.workouts
    var selectedDay by rememberSaveable(data.activeSplitId, data.nextWorkoutIndex) {
        mutableIntStateOf(data.nextWorkoutIndex % days.size.coerceAtLeast(1))
    }
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var planMenu by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf<Int?>(null) }
    val index = selectedDay.coerceIn(0, days.lastIndex.coerceAtLeast(0))
    val workout = days.getOrNull(index)
    if (!LocalInspectionMode.current) BackHandler(pager.currentPage == 1) {
        scope.launch { pager.animateScrollToPage(0) }
    }
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TRAINING PLAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.activeSplit?.name ?: "Your workouts", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { planMenu = true }) { Icon(AppIcons.More, "Training plan options") }
                DropdownMenu(planMenu, { planMenu = false }) {
                    DropdownMenuItem(text = { Text("Manage workouts & splits") }, onClick = { planMenu = false; onPlans() })
                    DropdownMenuItem(text = { Text("View history") }, onClick = { planMenu = false; onHistory() })
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("UP NEXT", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.draft?.name ?: next?.name ?: "Create a workout",
                            style = MaterialTheme.typography.headlineMedium)
                        Text("${state.draft?.exercises?.size ?: next?.exercises?.count { planned -> data.exercises.any { it.id == planned.exerciseId && !it.archived } } ?: 0} exercises · Your pace",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(AppIcons.Train, null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .65f))
                }
                Button(onClick = {
                    if (state.draft != null || next != null) onRoutine(state.draft?.workoutId ?: next?.id.orEmpty()) else onPlans()
                }, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semanticsLabel("Next workout action"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(if (state.draft != null) "Resume workout" else if (next == null) "Create workout" else "Start workout",
                        Modifier.weight(1f))
                    Icon(AppIcons.Arrow, null)
                }
            }
        }
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Overview", "Day Details").forEachIndexed { i, label ->
                Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(label) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().semanticsLabel("Training pages"),
            verticalAlignment = Alignment.Top) { page ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (days.isEmpty()) item {
                    EmptyMessage("No workout days yet", "Create a workout and add it to your training split.")
                }
                if (page == 0) itemsIndexed(days, key = { i, day -> "$i-${day.id}" }) { i, day ->
                    val exercises = day.exercises.filter { planned -> data.exercises.any { it.id == planned.exerciseId && !it.archived } }
                    val last = data.sessions.filter { it.workoutId == day.id || (it.workoutId == null && it.routine == day.name) }
                        .maxByOrNull { it.performedAt }
                    Card(onClick = {
                        if (index == i) scope.launch { pager.animateScrollToPage(1) }
                        else selectedDay = i
                    }, enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().semantics { selected = index == i },
                        shape = RoundedCornerShape(14.dp),
                        border = if (index == i) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Day ${i + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Text(day.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${exercises.size} exercises · ${exercises.sumOf { it.targets.size }} sets",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(last?.let { "Last trained ${dateLabel(it.performedAt)}" } ?: "Not started",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { dayMenu = i }, enabled = !state.saving) { Icon(AppIcons.More, "Day ${i + 1} options") }
                                DropdownMenu(dayMenu == i, { dayMenu = null }) {
                                    DropdownMenuItem(text = { Text("View day details") }, onClick = {
                                        selectedDay = i; dayMenu = null; scope.launch { pager.animateScrollToPage(1) }
                                    })
                                    DropdownMenuItem(text = { Text("Edit workout") }, onClick = { dayMenu = null; onEditWorkout(day.id) })
                                }
                            }
                        }
                    }
                }
                if (page == 1 && workout != null) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Day ${index + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Text(workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = { onEditWorkout(workout.id) }, enabled = !state.saving) { Icon(AppIcons.Edit, "Edit workout") }
                        }
                    }
                    val exercises = workout.exercises.mapNotNull { planned ->
                        data.exercises.find { it.id == planned.exerciseId && !it.archived }?.let { planned to it }
                    }
                    if (exercises.isEmpty()) item { EmptyMessage("No exercises yet", "Edit this workout to add exercises from your library.") }
                    itemsIndexed(exercises, key = { _, item -> item.second.id }) { i, (planned, exercise) ->
                        Surface(onClick = { onProgress(exercise.id) }, color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.size(48.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text("${i + 1}", color = MaterialTheme.colorScheme.primary) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(if (planned.maxReps) "${planned.targets.size} sets · Max reps" else
                                        "${planned.targets.size} sets · " + planned.targets.map { exercise.display(it.value) + (it.weightKg?.let { kg -> " · $kg kg" } ?: "") }.let {
                                            if (it.distinct().size == 1) it.first() else it.joinToString(" / ")
                                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${formatRestDuration(planned.restSeconds)} rest", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(AppIcons.Arrow, "View ${exercise.name} history", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.draft != null) "IN PROGRESS" else "${days.size} WORKOUT DAYS", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.draft?.name ?: workout?.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = {
                    if (state.draft != null || workout != null) onRoutine(state.draft?.workoutId ?: workout?.id.orEmpty()) else onPlans()
                }, enabled = !state.saving, modifier = Modifier.heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary)) {
                    Text(if (state.draft != null) "Resume workout" else if (workout == null) "Create workout" else "Start workout")
                }
            }
        }
    }
}
