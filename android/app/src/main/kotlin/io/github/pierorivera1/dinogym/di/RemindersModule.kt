package io.github.pierorivera1.dinogym.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pierorivera1.dinogym.api.DinoGymApi
import io.github.pierorivera1.dinogym.api.apis.DevicesApi
import io.github.pierorivera1.dinogym.reminders.DeviceRegistry
import io.github.pierorivera1.dinogym.reminders.FcmDeviceRegistry
import io.github.pierorivera1.dinogym.reminders.FirebasePushTokens
import io.github.pierorivera1.dinogym.reminders.PushTokens

/** Reminders (task 24): device registration and the FCM token. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {

    @Binds
    abstract fun bindDeviceRegistry(registry: FcmDeviceRegistry): DeviceRegistry

    @Binds
    abstract fun bindPushTokens(tokens: FirebasePushTokens): PushTokens

    companion object {
        /** Registration sets its own bearer token per call. */
        @Provides
        fun provideDevicesApi(api: DinoGymApi): DevicesApi = api.devices
    }
}
