package io.github.pierorivera1.dinogym.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.SyncTracking
import io.github.pierorivera1.dinogym.data.repository.CycleWriter
import io.github.pierorivera1.dinogym.data.repository.ExerciseRepository
import io.github.pierorivera1.dinogym.data.repository.HistoryRepository
import io.github.pierorivera1.dinogym.data.repository.ProgramRepository
import io.github.pierorivera1.dinogym.data.repository.RoutineRepository
import io.github.pierorivera1.dinogym.data.repository.WorkoutRepository
import io.github.pierorivera1.dinogym.data.seed.FirstRunSeeder
import io.github.pierorivera1.dinogym.data.seed.SeedStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand. */
class TestClock(var now: Long = 1_700_000_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)

    fun advanceMinutes(minutes: Long) {
        now += minutes * 60_000
    }
}

/**
 * An in-memory database with the real repositories wired by hand, as Hilt would.
 * Call [close] in `@After`.
 */
class TestDatabase {
    val clock = TestClock()
    private val context: Context = ApplicationProvider.getApplicationContext()

    val db: DinoDatabase = Room.inMemoryDatabaseBuilder(context, DinoDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .addCallback(SyncTracking.callback)
        .build()

    private val cycleWriter = CycleWriter(db.programDao(), clock)

    val programs = ProgramRepository(db, db.programDao(), cycleWriter, clock)
    val routines = RoutineRepository(db, db.routineDao(), db.exerciseDao(), db.programDao(), cycleWriter, clock)
    val exercises = ExerciseRepository(db.exerciseDao(), clock)
    val workouts = WorkoutRepository(db, db.sessionDao(), db.routineDao(), db.exerciseDao(), db.programDao(), clock)
    val history = HistoryRepository(db, db.sessionDao(), db.routineDao(), db.exerciseDao(), db.programDao(), clock)
    val seeder = FirstRunSeeder(db, SeedStrings(context), clock, CoroutineScope(Dispatchers.IO))

    fun close() = db.close()
}
