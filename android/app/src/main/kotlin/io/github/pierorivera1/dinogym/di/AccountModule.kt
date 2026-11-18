package io.github.pierorivera1.dinogym.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pierorivera1.dinogym.BuildConfig
import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.apis.AuthApi
import io.github.pierorivera1.dinogym.data.account.DataStoreSessionStore
import io.github.pierorivera1.dinogym.data.account.KeystoreTokenCipher
import io.github.pierorivera1.dinogym.data.account.SessionStore
import io.github.pierorivera1.dinogym.data.account.TokenCipher
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Qualifier
import javax.inject.Singleton

/** The DataStore file that holds the encrypted session (task 20). Never synced or backed up. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountStore

/** The API client and the session storage. SessionManager is injected by its constructor. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    abstract fun bindSessionStore(store: DataStoreSessionStore): SessionStore

    companion object {
        /** The generated client (decision 60) over OkHttp, pointed at staging or prod by build type. */
        @Provides
        @Singleton
        fun provideApi(): DinoGymApi = DinoGymApi(BuildConfig.API_BASE_URL, OkHttp.create())

        @Provides
        fun provideAuthApi(api: DinoGymApi): AuthApi = api.auth

        @Provides
        @Singleton
        fun provideTokenCipher(): TokenCipher = KeystoreTokenCipher()

        @Provides
        @Singleton
        @AccountStore
        fun provideAccountStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("account") })
    }
}
