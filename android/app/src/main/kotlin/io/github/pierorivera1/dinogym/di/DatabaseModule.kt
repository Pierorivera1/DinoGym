package io.github.pierorivera1.dinogym.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.ALL_MIGRATIONS
import io.github.pierorivera1.dinogym.data.local.ExerciseDao
import io.github.pierorivera1.dinogym.data.local.PreferenceDao
import io.github.pierorivera1.dinogym.data.local.ProgramDao
import io.github.pierorivera1.dinogym.data.local.RoutineDao
import io.github.pierorivera1.dinogym.data.local.SessionDao
import io.github.pierorivera1.dinogym.data.local.WeighInDao
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** Provides the Room database and its DAOs. Repositories are injected by their constructors. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DinoDatabase =
        Room.databaseBuilder(context, DinoDatabase::class.java, DinoDatabase.FILE_NAME)
            // The bundled driver ships the same SQLite on every phone (decision 38).
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(io)
            // Real migrations only: the app holds workout history, so it never falls back to a wipe.
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
            .build()

    @Provides
    fun provideProgramDao(db: DinoDatabase): ProgramDao = db.programDao()

    @Provides
    fun provideRoutineDao(db: DinoDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideExerciseDao(db: DinoDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideSessionDao(db: DinoDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideWeighInDao(db: DinoDatabase): WeighInDao = db.weighInDao()

    @Provides
    fun providePreferenceDao(db: DinoDatabase): PreferenceDao = db.preferenceDao()
}
