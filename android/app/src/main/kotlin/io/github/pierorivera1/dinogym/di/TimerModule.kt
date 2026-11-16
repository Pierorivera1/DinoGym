package io.github.pierorivera1.dinogym.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** The DataStore file that holds the rest in progress (its own file, apart from settings). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RestTimerStore

/** Provides the rest timer's storage. The timer classes are injected by their constructors. */
@Module
@InstallIn(SingletonComponent::class)
object TimerModule {

    @Provides
    @Singleton
    @RestTimerStore
    fun provideRestTimerStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("rest_timer") })
}
