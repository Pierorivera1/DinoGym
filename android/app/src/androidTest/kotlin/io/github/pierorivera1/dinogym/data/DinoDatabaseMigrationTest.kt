package io.github.pierorivera1.dinogym.data

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.pierorivera1.dinogym.data.local.ALL_MIGRATIONS
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.MIGRATION_1_2
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks each migration against the exported schemas in `android/app/schemas/`: the database
 * after the migration must match the schema Room expects, and existing rows must survive.
 * Runs in CI only (instrumented).
 */
@RunWith(AndroidJUnit4::class)
class DinoDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        instrumentation.targetContext.getDatabasePath(DB_NAME),
        BundledSQLiteDriver(),
        DinoDatabase::class,
    )

    @Test
    fun migrate1To2KeepsWorkoutsAndAddsBodyWeightTables() = runTest {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO routine (id, name, updatedAt, deletedAt) VALUES ('r1', 'Pull', 1, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))

        db.prepare("SELECT name FROM routine WHERE id = 'r1'").use { statement ->
            statement.step()
            assertEquals("Pull", statement.getText(0))
        }
        db.execSQL("INSERT INTO weigh_in (id, measuredAt, weightKg, updatedAt, deletedAt) VALUES ('w1', 1, 78.3, 1, NULL)")
        db.execSQL("INSERT INTO preference (`key`, value, updatedAt, deletedAt) VALUES ('body_weight_goal_kg', '76.0', 1, NULL)")
        db.prepare("SELECT COUNT(*) FROM weigh_in").use { statement ->
            statement.step()
            assertEquals(1L, statement.getLong(0))
        }
        db.close()
    }

    /** Every version step has a migration, so a new version without one fails here. */
    @Test
    fun migrateFromFirstVersionToLatest() = runTest {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(LATEST_VERSION, ALL_MIGRATIONS).close()
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val LATEST_VERSION = 2
    }
}
