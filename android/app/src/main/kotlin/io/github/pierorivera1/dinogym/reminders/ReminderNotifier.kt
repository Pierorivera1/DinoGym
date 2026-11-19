package io.github.pierorivera1.dinogym.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pierorivera1.dinogym.MainActivity
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import io.github.pierorivera1.dinogym.ui.home.HomeStateMapper
import io.github.pierorivera1.dinogym.ui.home.HomeUiState
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a server message into a notification (decision 104): reads Room as Home does, asks
 * [ReminderContent] what to say, and words it from the string resources in the phone's
 * language. Each kind has its own channel, so either can be silenced in Android settings.
 * Tapping opens the app on Home.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    private val history: HistoryRepository,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun areNotificationsAllowed(): Boolean = manager.areNotificationsEnabled()

    /** Creates or renames the two channels. Safe on every app start. */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_DAY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.reminders_channel_day))
                    .setDescription(context.getString(R.string.reminders_channel_day_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_WEEK, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.reminders_channel_week))
                    .setDescription(context.getString(R.string.reminders_channel_week_description))
                    .build(),
            ),
        )
    }

    /** Shows the [kind] reminder, or nothing when [ReminderContent] has nothing to say. */
    suspend fun show(kind: ReminderKind) {
        if (!areNotificationsAllowed()) return
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.millis()).atZone(zone).toLocalDate()
        val home = homeState(zone)
        val notice = when (kind) {
            ReminderKind.WORKOUT_DAY -> ReminderContent.workoutDay(home, today)
            ReminderKind.WEEKLY_SUMMARY -> ReminderContent.weekly(home, history.observeFinishedSessions().first(), today, zone)
        } ?: return
        val (title, body) = words(notice)
        val channel = if (kind == ReminderKind.WORKOUT_DAY) CHANNEL_DAY else CHANNEL_WEEK
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openHome())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        post(if (kind == ReminderKind.WORKOUT_DAY) ID_DAY else ID_WEEK, notification)
    }

    /** Home's state, read once: the same mapper the Home screen uses. */
    private suspend fun homeState(zone: ZoneId): HomeUiState {
        val today = Instant.ofEpochMilli(clock.millis()).atZone(zone).toLocalDate()
        val program = programs.observeActiveProgram().first()
        val inProgress = workouts.observeInProgressSession().first()
        val last = history.observeLastFinishedSession().first()
        val routineId = inProgress?.routineId ?: program?.nextStep?.step?.routineId
        val routine = routineId?.let { routines.observeRoutine(it).first() }
        return HomeStateMapper.map(today, zone, program, inProgress, last, routine, emptyMap())
    }

    private fun words(notice: ReminderNotice): Pair<String, String> {
        val res = context.resources
        return when (notice) {
            is ReminderNotice.WorkoutDay -> context.getString(R.string.reminders_day_title, notice.routineName) to
                context.getString(
                    R.string.reminders_day_body,
                    res.getQuantityString(R.plurals.reminders_exercises, notice.exercises, notice.exercises),
                    res.getQuantityString(R.plurals.reminders_sets, notice.sets, notice.sets),
                )
            is ReminderNotice.Week -> {
                val hours = (notice.minutes / 60).toInt()
                val minutes = (notice.minutes % 60).toInt()
                val time = if (hours > 0) {
                    context.getString(R.string.reminders_hours_minutes, hours, minutes)
                } else {
                    context.getString(R.string.reminders_minutes, minutes)
                }
                val body = context.getString(
                    R.string.reminders_week_body,
                    res.getQuantityString(R.plurals.reminders_sets, notice.sets, notice.sets),
                    time,
                )
                res.getQuantityString(R.plurals.reminders_week_title, notice.workouts, notice.workouts) to
                    (notice.upNext?.let { "$body ${context.getString(R.string.reminders_week_next, it)}" } ?: body)
            }
            is ReminderNotice.EmptyWeek -> context.getString(R.string.reminders_week_empty_title) to
                context.getString(R.string.reminders_week_empty_body, notice.upNext)
        }
    }

    // Checked in show(); without permission nothing is posted.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: android.app.Notification) {
        manager.notify(id, notification)
    }

    /** Opens the app like the launcher does, which lands on Home. */
    private fun openHome(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val CHANNEL_DAY = "reminder_workout_day"
        const val CHANNEL_WEEK = "reminder_weekly"
        const val ID_DAY = 2001
        const val ID_WEEK = 2002
    }
}
