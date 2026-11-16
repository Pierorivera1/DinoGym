package io.github.pierorivera1.dinogym.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pierorivera1.dinogym.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wakes the app at the end of a rest, even in Doze and with the process dead, by asking
 * [AlarmManager] to deliver a broadcast to [RestOverReceiver].
 *
 * It uses [AlarmManager.setAlarmClock] (decision 42): alarm clocks are never delayed by Doze
 * and are not rate-limited, unlike `setExactAndAllowWhileIdle`, which allows about one alarm
 * a minute in Doze and back-to-back rests would hit that. The system shows a small alarm
 * icon in the status bar during a rest; tapping the alarm in the system UI opens the app.
 *
 * There is only ever one rest alarm: scheduling again replaces the previous one, because
 * the PendingIntent is the same (same receiver, same request code).
 */
@Singleton
class RestAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Schedules (or moves) the alarm to the end of [rest]. */
    fun schedule(rest: RestTimerState) {
        val operation = pendingIntent(rest.nextLabel)
        // USE_EXACT_ALARM (API 33+) cannot be revoked, and SCHEDULE_EXACT_ALARM is granted by
        // default on API 31–32, so the fallback should never run. If it does, an inexact
        // while-idle alarm still fires, possibly late, instead of throwing SecurityException.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(rest.endsAtMillis, showAppIntent()), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rest.endsAtMillis, operation)
        }
    }

    /** Cancels the alarm, if one is set. */
    fun cancel() {
        // Alarms match on the Intent without its extras, so any label cancels the same alarm.
        alarmManager.cancel(pendingIntent(nextLabel = ""))
    }

    /** What the system opens when the user taps the alarm (for example in the quick settings). */
    private fun showAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, SHOW_REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pendingIntent(nextLabel: String): PendingIntent {
        val intent = Intent(context, RestOverReceiver::class.java)
            .putExtra(RestOverReceiver.EXTRA_NEXT_LABEL, nextLabel)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_CODE = 0
        const val SHOW_REQUEST_CODE = 1
    }
}
