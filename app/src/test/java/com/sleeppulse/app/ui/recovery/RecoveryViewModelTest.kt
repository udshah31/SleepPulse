package com.sleeppulse.app.ui.recovery

import app.cash.turbine.test
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun night(date: LocalDate, score: Int = 70) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 60,
        avgHrvMillis = 50.0,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `recordedNightsCount reflects actual recentNights size, not a hardcoded placeholder`() = runTest {
        val repository = FakeSleepRepository()
        repository.nightsFlow.value = listOf(
            night(LocalDate.of(2026, 7, 21)),
            night(LocalDate.of(2026, 7, 20)),
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
        repository.nightsFlow.value = List(10) { night(LocalDate.of(2026, 7, 21).minusDays(it.toLong())) }
        val viewModel = RecoveryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial loading state

            viewModel.onIntent(RecoveryIntent.Load)
            val loaded = awaitItem()

            assertEquals(4, loaded.recordedNightsCount)
        }
    }
}
