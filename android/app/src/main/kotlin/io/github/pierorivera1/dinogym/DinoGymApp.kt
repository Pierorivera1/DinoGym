package io.github.pierorivera1.dinogym

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.pierorivera1.dinogym.data.seed.FirstRunSeeder
import javax.inject.Inject

/** Application class. Hilt builds its dependency graph from here. */
@HiltAndroidApp
class DinoGymApp : Application() {

    @Inject
    lateinit var firstRunSeeder: FirstRunSeeder

    override fun onCreate() {
        super.onCreate()
        // Runs on the IO dispatcher; screens observe Flows, so they update when the seed lands.
        firstRunSeeder.seedInBackground()
    }
}
