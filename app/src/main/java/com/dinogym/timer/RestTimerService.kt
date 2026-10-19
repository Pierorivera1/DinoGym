package com.dinogym.timer

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import androidx.core.app.NotificationCompat
import com.dinogym.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uses elapsed time so changing the wall clock cannot change a running rest. */
data class RestCountdown(val deadline: Long = 0, val pausedMs: Long = 0) {
    fun remaining(now: Long) = if (deadline > 0) (deadline - now).coerceAtLeast(0) else pausedMs
    val active get() = deadline > 0 || pausedMs > 0
    fun adjust(seconds: Int, now: Long): RestCountdown {
        val adjusted = (remaining(now) + seconds * 1000L).coerceIn(0, 3600000)
        return if (deadline > 0) RestCountdown(now + adjusted) else RestCountdown(pausedMs = adjusted)
    }
}

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var tone: ToneGenerator? = null
    private val finish = Runnable {
        mutable.value = RestCountdown()
        persist()
        // Respect the device alarm volume and audio policy; never vibrate or show a heads-up alert.
        // TONE_PROP_BEEP is fixed at 35 ms even when a longer duration is requested.
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 80).also { it.startTone(ToneGenerator.TONE_DTMF_0, 2000) } }.getOrNull()
        handler.postDelayed({ tone?.release(); tone = null; stopSelf() }, 2000)
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("rest-timer", MODE_PRIVATE)
        mutable.value = RestCountdown(prefs.getLong("deadline", 0), prefs.getLong("paused", 0))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("rest", "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacksAndMessages(null)
        tone?.release(); tone = null
        val now = SystemClock.elapsedRealtime()
        when (intent?.action) {
            "start" -> mutable.value = RestCountdown(now + intent.getIntExtra("seconds", 165).coerceIn(1, 3600) * 1000L)
            "pause" -> mutable.value = RestCountdown(pausedMs = mutable.value.remaining(now))
            "resume" -> mutable.value = RestCountdown(now + mutable.value.pausedMs)
            "add15" -> mutable.value = mutable.value.adjust(15, now)
            "subtract15" -> mutable.value = mutable.value.adjust(-15, now)
            "stop" -> { mutable.value = RestCountdown(); persist(); stopSelf(); return START_NOT_STICKY }
        }
        persist()
        if (!mutable.value.active) { stopSelf(); return START_NOT_STICKY }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val skip = PendingIntent.getService(this, 1, Intent(this, RestTimerService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val remaining = mutable.value.remaining(now)
        val notice = NotificationCompat.Builder(this, "rest")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Rest timer")
            .setContentText(if (mutable.value.deadline > 0) "Resting" else "Paused")
            .setContentIntent(open).setOnlyAlertOnce(true).setSilent(true).setOngoing(true)
            .setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(mutable.value.deadline > 0)
            .setChronometerCountDown(true).addAction(0, "Skip", skip).build()
        startForeground(42, notice)
        wakeLock?.let { if (it.isHeld) it.release() }
        if (mutable.value.deadline > 0) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DinoGym:rest").apply {
                acquire(remaining + 3000)
            }
            handler.postDelayed(finish, remaining)
        }
        return START_STICKY
    }

    private fun persist() {
        getSharedPreferences("rest-timer", MODE_PRIVATE).edit()
            .putLong("deadline", mutable.value.deadline).putLong("paused", mutable.value.pausedMs).apply()
    }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    companion object {
        private val mutable = MutableStateFlow(RestCountdown())
        val state = mutable.asStateFlow()
        fun start(context: Context, seconds: Int) {
            if (seconds == 0) { stop(context); return }
            context.startForegroundService(Intent(context, RestTimerService::class.java).setAction("start").putExtra("seconds", seconds))
        }
        fun command(context: Context, action: String) {
            if (mutable.value.active) context.startService(Intent(context, RestTimerService::class.java).setAction(action))
        }
        fun stop(context: Context) {
            mutable.value = RestCountdown()
            context.getSharedPreferences("rest-timer", MODE_PRIVATE).edit().clear().apply()
            context.stopService(Intent(context, RestTimerService::class.java))
        }
    }
}
