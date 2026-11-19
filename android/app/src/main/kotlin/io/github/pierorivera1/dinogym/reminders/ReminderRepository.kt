package io.github.pierorivera1.dinogym.reminders

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.PreferenceDao
import io.github.pierorivera1.dinogym.data.local.PreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the reminder settings (decision 105). Every change also writes this
 * phone's time zone, so the server schedules in the zone the person is in; [followZone]
 * keeps it current after a trip. Sync carries the rows up and the server reconciles.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val db: DinoDatabase,
    private val preferenceDao: PreferenceDao,
    private val clock: Clock,
) {
    /** The phone's zone. A property so tests can set one. */
    internal var zone: () -> ZoneId = { ZoneId.systemDefault() }

    val settings: Flow<ReminderSettings> = combine(
        preferenceDao.observe(ReminderKeys.WORKOUT_DAY),
        preferenceDao.observe(ReminderKeys.WEEKLY),
        preferenceDao.observe(ReminderKeys.TIME),
    ) { day, week, time ->
        ReminderSettings(ReminderRules.decodeOn(day), ReminderRules.decodeOn(week), ReminderRules.decodeTime(time))
    }.distinctUntilChanged()

    suspend fun setWorkoutDay(on: Boolean) = write(ReminderKeys.WORKOUT_DAY, ReminderRules.encodeOn(on))

    suspend fun setWeekly(on: Boolean) = write(ReminderKeys.WEEKLY, ReminderRules.encodeOn(on))

    suspend fun setTime(time: LocalTime) = write(ReminderKeys.TIME, ReminderRules.encodeTime(time))

    /**
     * At app start: when a reminder is on and the stored zone is not this phone's, stores this
     * one, so the reminders move with the person (decision 105). Otherwise writes nothing.
     */
    suspend fun followZone() {
        val current = settings.first()
        if (!current.workoutDay && !current.weekly) return
        val zoneId = zone().id
        if (preferenceDao.observe(ReminderKeys.TIME_ZONE).first() == zoneId) return
        db.withWriteTransaction { put(ReminderKeys.TIME_ZONE, zoneId, clock.millis()) }
    }

    private suspend fun write(key: String, value: String) {
        val now = clock.millis()
        db.withWriteTransaction {
            put(key, value, now)
            put(ReminderKeys.TIME_ZONE, zone().id, now)
        }
    }

    private suspend fun put(key: String, value: String, now: Long) {
        preferenceDao.put(PreferenceEntity(key, value, updatedAt = now))
    }
}
