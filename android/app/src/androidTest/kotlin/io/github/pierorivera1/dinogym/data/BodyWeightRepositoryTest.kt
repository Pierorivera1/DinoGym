package io.github.pierorivera1.dinogym.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pierorivera1.dinogym.data.repository.BodyWeightRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BodyWeightRepositoryTest {
    private val t = TestDatabase()
    private val repo = BodyWeightRepository(t.db.weighInDao(), t.db.preferenceDao(), t.clock)
    private val zone = ZoneOffset.UTC
    private val today: LocalDate get() = LocalDate.ofEpochDay(t.clock.now / 86_400_000)

    @After
    fun tearDown() = t.close()

    @Test
    fun weighInsAreNewestFirstAndRounded() = runTest {
        repo.addWeighIn(80.04, today.minusDays(2), zone)
        repo.addWeighIn(79.46, today, zone)

        val list = repo.observeWeighIns().first()
        assertEquals(listOf(79.5, 80.0), list.map { it.weightKg })
        assertEquals(t.clock.now, list.first().measuredAt)
        assertEquals(79.5, repo.latestWeightKg())
    }

    @Test
    fun aFutureDayIsStoredAsNow() = runTest {
        repo.addWeighIn(80.0, today.plusDays(3), zone)
        assertEquals(t.clock.now, repo.observeWeighIns().first().single().measuredAt)
    }

    @Test
    fun deleteIsSoftAndUndoable() = runTest {
        val id = repo.addWeighIn(80.0, today, zone)
        repo.deleteWeighIn(id)
        assertTrue(repo.observeWeighIns().first().isEmpty())
        repo.restoreWeighIn(id)
        assertEquals(id, repo.observeWeighIns().first().single().id)
    }

    @Test
    fun goalCanBeSetChangedAndCleared() = runTest {
        assertNull(repo.observeGoalKg().first())
        repo.setGoalKg(76.0)
        repo.setGoalKg(75.5)
        assertEquals(75.5, repo.observeGoalKg().first())
        repo.setGoalKg(null)
        assertNull(repo.observeGoalKg().first())
        repo.setGoalKg(74.0)
        assertEquals(74.0, repo.observeGoalKg().first())
    }
}
