package com.dinogym.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.dinogym.data.Session
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HistoryCalendar(sessions: List<Session>, selectedDay: String?, onSelect: (String?) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val zone = ZoneId.systemDefault()
    val days = sessions.groupingBy { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }.eachCount()
    val offset = month.atDay(1).dayOfWeek.value - 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() },
                modifier = Modifier.semanticsLabel("Previous month")) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() },
                modifier = Modifier.semanticsLabel("Next month")) { Text("›") }
        }
        Row {
            DayOfWeek.entries.forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row {
                repeat(7) { column ->
                    val day = week * 7 + column - offset + 1
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val date = month.atDay(day)
                        val count = days[date] ?: 0
                        val isSelected = selectedDay == date.toString()
                        TextButton(onClick = { onSelect(if (isSelected) null else date.toString()) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semanticsLabel("${date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))}, $count workouts")
                                .semantics { selected = isSelected },
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (count > 0 || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.toString())
                                Text(if (count > 0) "•" else "", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        Text("• Workout logged", style = MaterialTheme.typography.labelSmall)
        if (selectedDay != null) TextButton(onClick = { onSelect(null) }) { Text("Show all workouts") }
    }
}
