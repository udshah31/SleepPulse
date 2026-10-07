package com.sleeppulse.app.ui.recovery

import app.cash.turbine.test
import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.scoring.TrendDirection
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun night(
        date: LocalDate,
        score: Int = 70,
        avgHeartRateBpm: Int = 60,
        avgHrvMillis: Double = 50.0,
        tags: List<String> = emptyList(),
    ) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
        tags = tags,
    )

    @Test
    fun `repeated Load does not create duplicate collectors`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = RecoveryViewModel(repository)

        viewModel.onIntent(RecoveryIntent.Load)
        viewModel.onIntent(RecoveryIntent.Load)
        advanceUntilIdle()

        assertEquals(1, repository.recentNightsCallCount)
    }

    @Test
    fun `recordedNightsCount reflects actual recentNights size, not a hardcoded placeholder`() = runTest {
        val repository = FakeSleepRepository()
        repository.nightsFlow.value = listOf(
            night(LocalDate(2026, 7, 21)),
            night(LocalDate(2026, 7, 20)),
        )
        val viewModel = RecoveryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial loading state

            viewModel.onIntent(RecoveryIntent.Load)
            val loaded = awaitItem()

            assertEquals(2, loaded.recordedNightsCount)
        }
    }

    @Test
    fun `recordedNightsCount is capped at the display maximum`() = runTest {
        val repository = FakeSleepRepository()
        repository.nightsFlow.value = List(10) { night(LocalDate(2026, 7, 21).minus(it, DateTimeUnit.DAY)) }
        val viewModel = RecoveryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial loading state

            viewModel.onIntent(RecoveryIntent.Load)
            val loaded = awaitItem()

            assertEquals(4, loaded.recordedNightsCount)
        }
    }

    @Test
    fun `hrv and resting heart rate trends populate once 14 nights of history exist`() = runTest {
        val repository = FakeSleepRepository()
        val recentLowHrv = (0 until 7).map { night(LocalDate(2026, 7, 21).minus(it, DateTimeUnit.DAY), avgHrvMillis = 30.0) }
        val priorHighHrv = (7 until 14).map { night(LocalDate(2026, 7, 21).minus(it, DateTimeUnit.DAY), avgHrvMillis = 60.0) }
        repository.nightsFlow.value = recentLowHrv + priorHighHrv
        val viewModel = RecoveryViewModel(repository)

        viewModel.state.test {
            awaitItem()
            viewModel.onIntent(RecoveryIntent.Load)
            val loaded = awaitItem()

            assertEquals(TrendDirection.FALLING, loaded.hrvTrend?.direction)
            assertEquals(TrendDirection.STABLE, loaded.restingHeartRateTrend?.direction)
        }
    }

    @Test
    fun `no nights gives no advice instead of low-debt and irregular-schedule claims`() = runTest {
        val viewModel = RecoveryViewModel(FakeSleepRepository())

        viewModel.state.test {
            awaitItem()
            viewModel.onIntent(RecoveryIntent.Load)

            assertEquals(null, awaitItem().personalizedAdvice)
        }
    }

    @Test
    fun `one night speaks to sleep debt but not to schedule consistency`() = runTest {
        val repository = FakeSleepRepository()
        repository.nightsFlow.value = listOf(night(LocalDate(2026, 7, 21)))
        val viewModel = RecoveryViewModel(repository)

        viewModel.state.test {
            awaitItem()
            viewModel.onIntent(RecoveryIntent.Load)
            val advice = awaitItem().personalizedAdvice

            assertEquals(true, advice?.contains("sleep debt"))
            assertEquals(false, advice?.contains("irregular"))
        }
    }

    @Test
    fun `tag correlations surface once a tag has enough occurrences`() = runTest {
        val repository = FakeSleepRepository()
        repository.nightsFlow.value = listOf(
            night(LocalDate(2026, 7, 21), score = 55, tags = listOf("caffeine")),
            night(LocalDate(2026, 7, 20), score = 58, tags = listOf("caffeine")),
            night(LocalDate(2026, 7, 19), score = 52, tags = listOf("caffeine")),
            night(LocalDate(2026, 7, 18), score = 90),
            night(LocalDate(2026, 7, 17), score = 92),
        )
        val viewModel = RecoveryViewModel(repository)

        viewModel.state.test {
            awaitItem()
            viewModel.onIntent(RecoveryIntent.Load)
            val loaded = awaitItem()

            assertEquals(1, loaded.tagCorrelations.size)
            assertEquals("caffeine", loaded.tagCorrelations.first().tag)
            assertEquals(true, loaded.personalizedAdvice?.contains("caffeine"))
        }
    }
}
