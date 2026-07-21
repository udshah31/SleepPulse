package com.sleeppulse.app.ui.history

import app.cash.turbine.test
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun summary(date: LocalDate, score: Int) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 60,
        avgHrvMillis = 50.0,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `Load with no nights produces empty non-loading state`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            assertEquals(HistoryState(), awaitItem())

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()
            assertEquals(emptyList<NightWithTrend>(), loaded.nights)
            assertEquals(false, loaded.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Load computes trend against the following list element`() = runTest {
        val repository = FakeSleepRepository()
        // Newest-first, as recentNights() promises: today (score 80), yesterday (score 60), day before (score 63)
        // trendAgainst() uses a >2-point threshold, so day-before must differ from yesterday by more than
        // 2 points to produce DOWN (the brief's literal score of 61 would only be a 1-point drop -> FLAT).
        val today = summary(LocalDate.of(2026, 7, 17), score = 80)
        val yesterday = summary(LocalDate.of(2026, 7, 16), score = 60)
        val dayBefore = summary(LocalDate.of(2026, 7, 15), score = 63)
        repository.nightsFlow.value = listOf(today, yesterday, dayBefore)

        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial empty state

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()

            assertEquals(3, loaded.nights.size)
            assertEquals(NightlySummary.Trend.UP, loaded.nights[0].trend) // 80 vs 60 -> UP
            assertEquals(NightlySummary.Trend.DOWN, loaded.nights[1].trend) // 60 vs 63 -> DOWN
            assertEquals(NightlySummary.Trend.FLAT, loaded.nights[2].trend) // no next element -> FLAT
            assertTrue(loaded.nights.map { it.summary } == listOf(today, yesterday, dayBefore))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Load with no nights produces null sleepDebt`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial loading state

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()

            assertEquals(null, loaded.sleepDebt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Load computes sleepDebt from recentNights`() = runTest {
        val repository = FakeSleepRepository()
        // Three nights each 60 min short of the 480-min target → 180 min total → MODERATE
        val shortNight = summary(LocalDate.of(2026, 7, 21), score = 70)
            .copy(totalSleepMinutes = 420)
        repository.nightsFlow.value = List(3) { shortNight }

        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()

            val debt = loaded.sleepDebt!!
            assertEquals(180, debt.deficitMinutes)
            assertEquals(3, debt.nightsInWindow)
            assertEquals(DebtLevel.MODERATE, debt.level)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Load computes consistencyScore from recentNights`() = runTest {
        val repository = FakeSleepRepository()
        // Three nights with same bedtime should give score 100
        val nights = List(3) { summary(LocalDate.of(2026, 7, 21 - it), score = 70).copy(bedtimeEpochMillis = 0L) }
        repository.nightsFlow.value = nights

        val viewModel = HistoryViewModel(repository)

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.onIntent(HistoryIntent.Load)
            val loaded = awaitItem()

            assertEquals(100, loaded.consistencyScore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
