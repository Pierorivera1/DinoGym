package io.github.pierorivera1.dinogym.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.reminders.ReminderRules
import io.github.pierorivera1.dinogym.ui.components.PickerWheel
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PixelSwitch
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.RaisedBox
import io.github.pierorivera1.dinogym.ui.components.ScrollPicker
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.components.dottedTopLine
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The phone's clock style, for "8:00 AM" or "8:00". */
internal fun reminderTime(time: LocalTime, is24Hour: Boolean, locale: Locale): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale))

/**
 * Settings' REMINDERS section (mock `Reminders.dc.html`, DESIGN.md "Reminders"). Signed in:
 * Workout day with its TIME button, Weekly summary, and a note; when Android blocks the
 * app's notifications, a `danger` line and ALLOW NOTIFICATIONS come first. Signed out: a
 * muted card that points to ACCOUNT. Nothing shows while the account is being read.
 */
@Composable
internal fun RemindersSection(state: RemindersUiState, is24Hour: Boolean, locale: Locale, actions: SettingsActions) {
    when (state) {
        RemindersUiState.Loading -> Unit
        RemindersUiState.SignedOut -> Field(stringResource(R.string.reminders_label)) { SignedOutCard() }
        is RemindersUiState.SignedIn -> Field(stringResource(R.string.reminders_label)) {
            SignedInCard(state, is24Hour, locale, actions)
            Text(
                stringResource(R.string.reminders_note),
                style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = DinoTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun SignedOutCard() {
    val colors = DinoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.surface2)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(SteppedShape).background(colors.surface2), contentAlignment = Alignment.Center) {
            PixelIcon(R.drawable.ic_px_bell, colors.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.reminders_signed_out_title),
                style = DinoTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.outline,
            )
            Text(
                stringResource(R.string.reminders_signed_out_body),
                style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun SignedInCard(state: RemindersUiState.SignedIn, is24Hour: Boolean, locale: Locale, actions: SettingsActions) {
    val colors = DinoTheme.colors
    val settings = state.settings
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline),
    ) {
        if (!state.notificationsAllowed) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .dottedBottomLine(colors.surface2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.reminders_blocked),
                    style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.danger,
                )
                SecondaryButton(stringResource(R.string.reminders_allow), actions.onAllowNotifications, Modifier.fillMaxWidth())
            }
        }
        ReminderSwitch(
            title = stringResource(R.string.reminders_workout_day),
            help = stringResource(R.string.reminders_workout_day_help),
            checked = settings.workoutDay,
            onCheckedChange = actions.onWorkoutDayChange,
        )
        if (settings.workoutDay) {
            RaisedBox(
                actions.onOpenReminderTime,
                colors.surface,
                colors.action,
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp).fillMaxWidth().height(52.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.reminders_time), style = DinoTheme.type.body.copy(fontSize = 15.sp), color = colors.outline)
                    Text(
                        reminderTime(settings.time, is24Hour, locale),
                        style = DinoTheme.type.button.copy(fontSize = 20.sp),
                        color = colors.outline,
                    )
                }
            }
        }
        ReminderSwitch(
            title = stringResource(R.string.reminders_weekly),
            help = stringResource(R.string.reminders_weekly_help, reminderTime(ReminderRules.WEEKLY_TIME, is24Hour, locale)),
            checked = settings.weekly,
            onCheckedChange = actions.onWeeklyChange,
            modifier = Modifier.dottedTopLine(colors.surface2),
        )
    }
}

/** A row of the card: title, help line and the switch; the whole row toggles. */
@Composable
private fun ReminderSwitch(
    title: String,
    help: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = DinoTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = colors.outline)
            Text(help, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.muted)
        }
        PixelSwitch(checked)
    }
}

/**
 * The time sheet (mock `Reminders.dc.html`, board 2): HOUR and MIN wheels (minutes in
 * 5-minute steps) and SAVE. The wheels' values live here until SAVE.
 */
@Composable
internal fun ReminderTimeSheet(initial: LocalTime, zoneCity: String, onSave: (LocalTime) -> Unit, onClose: () -> Unit) {
    val start = ReminderRules.pickerIndexes(initial)
    var hour by rememberSaveable { mutableIntStateOf(start.first) }
    var minute by rememberSaveable { mutableIntStateOf(start.second) }
    WindowSheet(stringResource(R.string.reminders_sheet_title), onClose, stringResource(R.string.cd_close)) {
        Text(
            stringResource(R.string.reminders_sheet_scope, zoneCity),
            style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = DinoTheme.colors.muted,
        )
        val hourLabel = stringResource(R.string.reminders_hour)
        val minLabel = stringResource(R.string.label_min)
        ScrollPicker(labels = listOf(hourLabel, minLabel)) {
            PickerWheel(
                label = hourLabel,
                values = ReminderRules.HOURS.map { it.toString().padStart(2, '0') },
                selectedIndex = hour,
                onSelect = { hour = it },
                modifier = Modifier.weight(1f),
            )
            PickerWheel(
                label = minLabel,
                values = ReminderRules.MINUTES.map { it.toString().padStart(2, '0') },
                selectedIndex = minute,
                onSelect = { minute = it },
                modifier = Modifier.weight(1f),
            )
        }
        PrimaryButton(
            stringResource(R.string.action_save),
            { onSave(ReminderRules.pickerTime(hour, minute)) },
            Modifier.fillMaxWidth().height(60.dp),
        )
    }
}
