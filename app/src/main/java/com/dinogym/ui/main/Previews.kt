package com.dinogym.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dinogym.data.*
import com.dinogym.theme.DinoGymTheme

private val previewData = WorkoutData(sessions = listOf(8, 9, 11, 10, 12, 13).mapIndexed { index, value ->
    Session("Pull", listOf(Entry(starterExercises[0], value, 2), Entry(starterExercises[1], 4, 2),
        Entry(starterExercises[2], 12, 3)), performedAt = 1791046800000L + index * 86400000L, id = "preview-$index")
})

@Preview(name = "Train · Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun TrainPreview() {
    DinoGymTheme(darkTheme = false) { HomeScreen(WorkoutState(previewData, loading = false), {}, {}, {}, { _, _ -> }, { _, _ -> }) }
}

@Preview(name = "Train · Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun DarkPreview() {
    DinoGymTheme(darkTheme = true) { HomeScreen(WorkoutState(previewData, loading = false), {}, {}, {}, { _, _ -> }, { _, _ -> }) }
}

@Preview(name = "Train · Tablet", showBackground = true, widthDp = 900, heightDp = 1000)
@Composable
fun TabletPreview() {
    DinoGymTheme(darkTheme = false) { HomeScreen(WorkoutState(previewData, loading = false), {}, {}, {}, { _, _ -> }, { _, _ -> }) }
}

@Preview(name = "Workout", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun WorkoutPreview() {
    DinoGymTheme(darkTheme = false) { WorkoutScreen("Pull", WorkoutState(previewData, loading = false), {}, {}) }
}

@Preview(name = "Exercise progress", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ProgressPreview() {
    DinoGymTheme(darkTheme = false) { ExerciseProgressScreen("pull-ups", previewData, {}) }
}

@Preview(name = "Exercises", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun ExercisesPreview() {
    DinoGymTheme(darkTheme = false) { HomeScreen(WorkoutState(previewData, loading = false), {}, {}, {}, { _, _ -> }, { _, _ -> }, initialTab = 2) }
}
