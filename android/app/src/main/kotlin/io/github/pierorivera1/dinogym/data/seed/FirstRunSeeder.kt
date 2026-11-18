package io.github.pierorivera1.dinogym.data.seed

import androidx.room3.withWriteTransaction
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.LocalFlagEntity
import io.github.pierorivera1.dinogym.data.repository.ProgramInstaller
import io.github.pierorivera1.dinogym.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts the owner's program ([OwnerProgramSeed]) on the first launch, and never again.
 *
 * "Never again" is a row in the `local_flag` table, written in the same transaction
 * as the seed. Either both are saved or neither is, so a crash halfway retries on
 * the next launch, and deleting every program later does not bring the seed back.
 * Clearing the app's data removes the flag too, which counts as a new install.
 */
@Singleton
class FirstRunSeeder @Inject constructor(
    private val db: DinoDatabase,
    private val strings: SeedStrings,
    private val installer: ProgramInstaller,
    private val clock: Clock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    /** Starts [seedIfNeeded] in the background; called from `DinoGymApp.onCreate`. */
    fun seedInBackground() {
        appScope.launch { seedIfNeeded() }
    }

    /** Inserts the seed unless it ran before. Returns true when it inserted it. */
    suspend fun seedIfNeeded(): Boolean = db.withWriteTransaction {
        val flags = db.localFlagDao()
        if (flags.get(SEEDED_FLAG) != null) return@withWriteTransaction false
        installer.installInTransaction(
            SeedResolver.resolve(OwnerProgramSeed.program, strings::get),
            ProgramInstaller.Activation.ALWAYS,
        )
        flags.put(LocalFlagEntity(SEEDED_FLAG, clock.millis().toString()))
        true
    }

    companion object {
        const val SEEDED_FLAG = "first_run_seed_done"
    }
}
