package io.github.pierorivera1.dinogym.data.repository

import io.github.pierorivera1.dinogym.data.local.PreferenceDao
import io.github.pierorivera1.dinogym.data.local.PreferenceEntity
import io.github.pierorivera1.dinogym.data.local.WeighInDao
import io.github.pierorivera1.dinogym.data.local.WeighInEntity
import io.github.pierorivera1.dinogym.data.local.newId
import io.github.pierorivera1.dinogym.domain.model.WeighIn
import io.github.pierorivera1.dinogym.domain.rules.BodyWeightRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Body weight (task 11): weigh-ins and the optional goal weight. Everything is local
 * (offline first); the rows carry `updatedAt` / `deletedAt` for sync later (decision 36).
 */
@Singleton
class BodyWeightRepository @Inject constructor(
    private val weighInDao: WeighInDao,
    private val preferenceDao: PreferenceDao,
    private val clock: Clock,
) {

    /** Every weigh-in, newest first. */
    fun observeWeighIns(): Flow<List<WeighIn>> =
        weighInDao.observeAll().map { rows -> rows.map { WeighIn(it.id, it.measuredAt, it.weightKg) } }

    /** The goal weight in kg, or null when none is set. */
    fun observeGoalKg(): Flow<Double?> = preferenceDao.observe(GOAL_KEY).map { it?.toDoubleOrNull() }

    /** The latest weigh-in's weight, where the log sheet starts. */
    suspend fun latestWeightKg(): Double? = weighInDao.getLatest()?.weightKg

    /**
     * Adds a weigh-in of [weightKg] (rounded to 0.1) on [day] in [zone]. Today is stored at the
     * current time; an earlier day keeps the current time of day, so the order within the list
     * stays natural. A future day is moved back to today. Returns the new id.
     */
    suspend fun addWeighIn(weightKg: Double, day: LocalDate, zone: ZoneId): String {
        val nowMillis = clock.millis()
        val now = ZonedDateTime.ofInstant(clock.instant(), zone)
        val measuredAt = if (!day.isBefore(now.toLocalDate())) {
            nowMillis
        } else {
            day.atTime(now.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        }
        val id = newId()
        weighInDao.insert(
            WeighInEntity(
                id = id,
                measuredAt = measuredAt,
                weightKg = BodyWeightRules.roundTenth(weightKg),
                updatedAt = nowMillis,
            ),
        )
        return id
    }

    /** Soft-deletes a weigh-in. [restoreWeighIn] undoes it. */
    suspend fun deleteWeighIn(id: String) {
        val now = clock.millis()
        weighInDao.setDeleted(id, deletedAt = now, now = now)
    }

    suspend fun restoreWeighIn(id: String) = weighInDao.setDeleted(id, deletedAt = null, now = clock.millis())

    /** Sets the goal weight, or clears it with null. */
    suspend fun setGoalKg(goalKg: Double?) {
        val now = clock.millis()
        if (goalKg == null) {
            preferenceDao.clear(GOAL_KEY, now)
        } else {
            preferenceDao.put(PreferenceEntity(GOAL_KEY, BodyWeightRules.roundTenth(goalKg).toString(), updatedAt = now))
        }
    }

    companion object {
        /** The `preference` key of the goal, stored as a plain decimal such as `76.0`. */
        const val GOAL_KEY = "body_weight_goal_kg"
    }
}
