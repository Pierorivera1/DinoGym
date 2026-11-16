package io.github.pierorivera1.dinogym.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseKind
import io.github.pierorivera1.dinogym.ui.components.CycleStrip
import io.github.pierorivera1.dinogym.ui.components.CycleStripItem
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.components.dottedTopLine
import io.github.pierorivera1.dinogym.ui.theme.ArtPixel
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.raised
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import io.github.pierorivera1.dinogym.ui.workout.WorkoutFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home (mock `Home.dc.html`): header with the date and the Settings gear, the mascot's text
 * box, the Up next card (routine, exercises, START and SKIP), the cycle strip and the last
 * workout. Stateless: it draws [state] and reports taps. The bottom bar belongs to the app shell.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onRestDone: () -> Unit,
    onEditRoutine: (routineId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(shortDate(state.today, locale), onOpenSettings)
        if (state.isLoading) return@Column

        val program = state.program
        TextBox(bubbleText(program), showMascot = true, modifier = Modifier.fillMaxWidth())

        if (program != null) {
            UpNext(program, onStart, onSkip, onRestDone, onEditRoutine)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallLabel(stringResource(R.string.label_cycle))
                CycleStrip(cycleItems(program))
            }
        } else {
            Text(
                stringResource(R.string.home_no_program_title),
                style = DinoTheme.type.title.copy(fontSize = 32.sp, lineHeight = 36.sp),
                color = colors.muted,
            )
        }
        LastWorkoutRow(state.lastWorkout, locale, onOpenHistory)
    }
}

@Composable
private fun Header(date: String, onOpenSettings: () -> Unit) {
    val colors = DinoTheme.colors
    val settings = stringResource(R.string.cd_settings)
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.home_title),
            style = DinoTheme.type.title.copy(fontSize = 26.sp, lineHeight = 30.sp),
            color = colors.outline,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
            RaisedBox(
                onClick = onOpenSettings,
                fill = colors.surface,
                border = colors.action,
                modifier = Modifier.size(48.dp).semantics { contentDescription = settings },
            ) {
                PixelIcon(R.drawable.ic_px_gear, colors.action, size = 24.dp)
            }
        }
    }
}

@Composable
private fun UpNext(
    program: HomeProgram,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onRestDone: () -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val colors = DinoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallLabel(stringResource(R.string.label_up_next))
            SmallLabel(stringResource(R.string.home_program_step, program.name.uppercase(), program.stepNumber, program.stepCount))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SteppedShape)
                .background(colors.surface)
                .raised(colors, pressed = false)
                .steppedBorder(colors.outline)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val next = program.next) {
                is HomeNext.Workout -> WorkoutCard(next, onStart, onSkip, onEditRoutine)
                is HomeNext.RestDay -> RestDayCard(next, onRestDone)
                HomeNext.EmptyCycle -> Text(
                    stringResource(R.string.home_bubble_empty_cycle),
                    style = DinoTheme.type.body,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    next: HomeNext.Workout,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val colors = DinoTheme.colors
    val editDescription = stringResource(R.string.cd_edit_routine, next.routineName)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CardTitle(next.routineName.uppercase())
            Text(
                stringResource(
                    R.string.home_meta,
                    pluralStringResource(R.plurals.home_exercise_count, next.exercises.size, next.exercises.size),
                    pluralStringResource(R.plurals.home_set_count, next.totalSets, next.totalSets),
                ),
                style = DinoTheme.type.body.copy(fontSize = 14.sp),
                color = colors.muted,
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(SteppedShape)
                .background(colors.surface2)
                .steppedBorder(colors.outline)
                .clickable(role = Role.Button) { onEditRoutine(next.routineId) }
                .semantics { contentDescription = editDescription },
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(R.drawable.ic_px_edit, colors.outline)
        }
    }
    Column {
        next.exercises.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .dottedTopLine(colors.surface2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    style = DinoTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = colors.outline,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (row.kind == ExerciseKind.SHOW_ONLY) {
                        stringResource(R.string.home_show_only)
                    } else {
                        WorkoutFormat.setsTimesReps(row.sets, row.repMin, row.repMax)
                    },
                    style = DinoTheme.type.button.copy(fontSize = 15.sp, lineHeight = 18.sp),
                    color = colors.muted,
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val name = next.routineName.uppercase()
        PrimaryButton(
            text = stringResource(if (next.inProgress) R.string.home_resume else R.string.home_start, name),
            onClick = onStart,
            icon = R.drawable.ic_px_start,
            modifier = Modifier.weight(1f),
        )
        if (!next.inProgress) {
            SecondaryButton(stringResource(R.string.action_skip), onSkip, Modifier.width(104.dp))
        }
    }
}

@Composable
private fun RestDayCard(next: HomeNext.RestDay, onRestDone: () -> Unit) {
    val colors = DinoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardTitle(stringResource(R.string.home_rest_title))
        if (next.followingRoutine != null) {
            Text(
                stringResource(R.string.home_rest_then, next.followingRoutine),
                style = DinoTheme.type.body.copy(fontSize = 14.sp),
                color = colors.muted,
            )
        }
    }
    Text(stringResource(R.string.home_cycle_rest), style = DinoTheme.type.title.copy(fontSize = 40.sp, lineHeight = 44.sp), color = colors.muted)
    PrimaryButton(
        text = stringResource(R.string.home_rest_done),
        onClick = onRestDone,
        icon = R.drawable.ic_px_check,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = DinoTheme.type.title.copy(fontSize = 40.sp, lineHeight = 40.sp),
        color = DinoTheme.colors.outline,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun LastWorkoutRow(last: LastWorkout?, locale: Locale, onOpenHistory: () -> Unit) {
    val colors = DinoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(ArtPixel, colors.surface2)
            .clickable(enabled = last != null, role = Role.Button, onClick = onOpenHistory)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val value = last?.let { "${it.routineName} · ${shortDate(it.date, locale)}" }
            ?: stringResource(R.string.home_last_none)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.muted)) { append(stringResource(R.string.home_last_label)) }
                append(" ")
                append(value)
            },
            style = DinoTheme.type.body.copy(fontSize = 14.sp),
            color = colors.outline,
        )
        if (last != null) PixelIcon(R.drawable.ic_px_start, colors.muted, scale = 1)
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text, style = DinoTheme.type.label.copy(fontSize = 13.sp, lineHeight = 16.sp), color = DinoTheme.colors.muted)
}

@Composable
private fun bubbleText(program: HomeProgram?): String {
    if (program == null) return stringResource(R.string.home_bubble_no_program)
    return when (val next = program.next) {
        is HomeNext.Workout -> when {
            next.inProgress -> stringResource(R.string.home_bubble_resume, next.routineName)
            next.lastSetOne != null -> stringResource(
                R.string.home_bubble_next_with_last,
                next.routineName,
                next.lastSetOne.reps,
                next.lastSetOne.exerciseName.lowercase(LocalConfiguration.current.locales[0]),
            )
            else -> stringResource(R.string.home_bubble_next, next.routineName)
        }
        is HomeNext.RestDay -> stringResource(R.string.home_bubble_rest)
        HomeNext.EmptyCycle -> stringResource(R.string.home_bubble_empty_cycle)
    }
}

@Composable
private fun cycleItems(program: HomeProgram): List<CycleStripItem> {
    val rest = stringResource(R.string.home_cycle_rest)
    val restDescription = stringResource(R.string.cd_cycle_rest)
    return program.cycle.map { cell ->
        CycleStripItem(
            label = cell.routineName?.uppercase() ?: rest,
            description = cell.routineName ?: restDescription,
            isRest = cell.routineName == null,
            isCurrent = cell.isCurrent,
        )
    }
}

/** "Mon 16 Nov" / "lun 16 nov", from the phone's language. */
internal fun shortDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date).replace(".", "")
