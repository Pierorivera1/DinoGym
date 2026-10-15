package com.dinogym.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeeklyProgress(val week: LocalDate, val value: Int)

fun Session.belongsTo(workout: WorkoutTemplate) =
    workoutId == workout.id || (workoutId == null && routine == workout.name)

fun weeklyProgress(history: List<Pair<Session, Entry>>, zone: ZoneId = ZoneId.systemDefault()): List<WeeklyProgress> =
    history.groupBy { (session, _) ->
        Instant.ofEpochMilli(session.performedAt).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }.toSortedMap().map { (week, entries) -> WeeklyProgress(week, entries.maxOf { it.second.value }) }
