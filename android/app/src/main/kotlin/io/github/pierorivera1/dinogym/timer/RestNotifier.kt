package io.github.pierorivera1.dinogym.timer

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the rest timer's two notifications.
 *
 * - **Running:** silent and ongoing, on a low-importance channel. The system draws the
 *   countdown itself (a chronometer counting down to the end time), so it stays correct
 *   with the screen off and the app's process dead; the app never updates it per second.
 * - **Rest over:** on a high-importance channel, so it makes a sound and vibrates.
 *
 * Both open the app when tapped.
 */
@Singleton
class RestNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * True when the app may post notifications: the `POST_NOTIFICATIONS` permission on
     * Android 13+, and the user has not turned notifications off for the app.
     * The rest screen asks for the permission when this is false.
     */
    fun areNotificationsAllowed(): Boolean = manager.areNotificationsEnabled()

    /**
     * Creates the channels, or renames them after a language change. Safe to call on every
     * app start: Android keeps the user's settings for a channel that already exists.
     */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.timer_channel_running))
                    .setDescription(context.getString(R.string.timer_channel_running_description))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_OVER, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.timer_channel_over))
                    .setDescription(context.getString(R.string.timer_channel_over_description))
                    .setVibrationEnabled(true)
                    .build(),
            ),
        )
    }

    /** Shows (or replaces) the counting-down notification for [rest]. */
    fun showRunning(rest: RestTimerState, nowMillis: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(withLabel(R.string.timer_notification_running, R.string.timer_notification_running_plain, rest.nextLabel))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(rest.endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            // Removed at the end time even if the alarm is late, so it never counts below 0:00.
            .setTimeoutAfter((rest.endsAtMillis - nowMillis).coerceAtLeast(1))
            .build()
        post(ID_RUNNING, notification)
    }

    /** Replaces the countdown with the "rest over" notification, which sounds and vibrates. */
    fun showRestOver(nextLabel: String) {
        manager.cancel(ID_RUNNING)
        val notification = NotificationCompat.Builder(context, CHANNEL_OVER)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(withLabel(R.string.timer_notification_over, R.string.timer_notification_over_plain, nextLabel))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        post(ID_OVER, notification)
    }

    /** Removes the countdown (rest skipped, or the app found it already over). */
    fun cancelRunning() = manager.cancel(ID_RUNNING)

    /** Removes the "rest over" notification, for when a new rest starts. */
    fun cancelRestOver() = manager.cancel(ID_OVER)

    // Without permission there is nothing to show; the timer itself still works.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: android.app.Notification) {
        if (!areNotificationsAllowed()) return
        manager.notify(id, notification)
    }

    private fun withLabel(withLabelRes: Int, plainRes: Int, label: String): String =
        if (label.isBlank()) context.getString(plainRes) else context.getString(withLabelRes, label)

    /** Opens the app like the launcher does: brings the existing task to the front. */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val CHANNEL_RUNNING = "rest_running"
        const val CHANNEL_OVER = "rest_over"
        const val ID_RUNNING = 1001
        const val ID_OVER = 1002
    }
}
