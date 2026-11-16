package io.github.pierorivera1.dinogym.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's Room database. Built in `di/DatabaseModule` with the bundled SQLite
 * driver (decision 38). The schema of each version is exported to
 * `android/app/schemas/` and committed, so schema changes show up in review.
 */
@Database(
    entities = [
        ProgramEntity::class,
        CycleStepEntity::class,
        RoutineEntity::class,
        ExerciseEntity::class,
        RoutineExerciseEntity::class,
        SessionEntity::class,
        SetEntryEntity::class,
        LocalFlagEntity::class,
        WeighInEntity::class,
        PreferenceEntity::class,
    ],
    // 2: weigh_in and preference (task 11). Every step has a migration in Migrations.kt.
    version = 2,
    exportSchema = true,
)
abstract class DinoDatabase : RoomDatabase() {
    abstract fun programDao(): ProgramDao
    abstract fun routineDao(): RoutineDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun localFlagDao(): LocalFlagDao
    abstract fun weighInDao(): WeighInDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        const val FILE_NAME = "dinogym.db"
    }
}
