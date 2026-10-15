package com.dinogym.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dinogym.data.DraftSet
import com.dinogym.data.Exercise

@Composable
internal fun SetRows(
    sets: List<DraftSet>, exercise: Exercise, enabled: Boolean,
    onChange: (List<DraftSet>) -> Unit, onLog: ((Int) -> Unit)? = null, maxReps: Boolean = false,
) {
    var weightVisible by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val hasWeight = sets.any { it.weight.isNotBlank() }
    val showWeight = weightVisible || hasWeight
    val activeSet = if (onLog != null) sets.indexOfFirst { !it.completed } else -1
    sets.forEachIndexed { index, set ->
        Surface(shape = RoundedCornerShape(12.dp), color = when {
            index == activeSet -> MaterialTheme.colorScheme.primaryContainer
            set.completed -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surface
        }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${index + 1}", Modifier.width(20.dp))
            OutlinedTextField(set.value, { value ->
                onChange(sets.mapIndexed { i, old -> if (i == index) old.copy(value = value.take(5), completed = false) else old })
            }, Modifier.weight(1f), label = { Text(exercise.unit) }, enabled = enabled, singleLine = true,
                supportingText = if (maxReps) {
                    { Text(if (onLog != null) "Max reps · enter completed reps" else "Max reps per set") }
                } else if (exercise.template != "{n} ${exercise.unit}") {
                    { Text(exercise.template.replace("{n}", set.value)) }
                } else null,
                isError = set.value.toIntOrNull() !in 0..9999 && !(maxReps && onLog == null && set.value.isBlank()),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (showWeight) OutlinedTextField(set.weight, { value ->
                onChange(sets.mapIndexed { i, old -> if (i == index) old.copy(weight = value.take(9), completed = false) else old })
            }, Modifier.weight(1f), label = { Text("kg (optional)") }, enabled = enabled, singleLine = true,
                isError = set.weight.isNotBlank() && (set.weight.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..9999.0 } != true),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (onLog != null) Checkbox(set.completed,
                onCheckedChange = { onLog(index) }, enabled = enabled && set.record() != null,
                modifier = Modifier.semanticsLabel("Log set ${index + 1}"))
            TextButton(onClick = { onChange(sets.filterIndexed { i, _ -> i != index }) },
                enabled = enabled && sets.size > 1, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(48.dp).semanticsLabel("Remove set ${index + 1}")) { Text("−") }
        }
        }
    }
    Row {
        TextButton(enabled = enabled && sets.size < 99,
            onClick = { onChange(sets + sets.last().copy(completed = false)) }) { Text("Add set") }
        TextButton(enabled = enabled && !hasWeight,
            onClick = { weightVisible = !weightVisible }) { Text(if (showWeight) "Hide weight" else "Add weight") }
    }
}

internal fun formatRestDuration(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
internal fun RestDurationDialog(
    initial: Int, onDismiss: () -> Unit, onStart: ((Int) -> Unit)? = null,
    startLabel: String = "Start", onSave: (Int) -> Unit,
) {
    var minutes by rememberSaveable { mutableStateOf((initial / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((initial % 60).toString().padStart(2, '0')) }
    val mins = minutes.toIntOrNull()
    val secs = seconds.toIntOrNull()
    val duration = if (mins in 0..60 && secs in 0..59) mins!! * 60 + secs!! else null
    val valid = duration in 0..3600
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rest timer") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes, { minutes = it.take(2) }, Modifier.weight(1f),
                    label = { Text("Minutes") }, singleLine = true, isError = mins !in 0..60 || !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(seconds, { seconds = it.take(2) }, Modifier.weight(1f),
                    label = { Text("Seconds") }, singleLine = true, isError = secs !in 0..59 || !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("0:00 disables automatic rest · maximum 60:00")
        }
    }, confirmButton = {
        TextButton(enabled = valid, onClick = {
            onSave(requireNotNull(duration)); onStart?.invoke(requireNotNull(duration)); onDismiss()
        }) { Text(if (onStart != null) startLabel else "Save") }
    }, dismissButton = {
        if (onStart != null) TextButton(enabled = valid, onClick = {
            onSave(requireNotNull(duration)); onDismiss()
        }) { Text("Set duration") }
        else TextButton(onClick = onDismiss) { Text("Cancel") }
    })
}
