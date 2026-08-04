package com.sleeppulse.app.notifications

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.FakeSleepSummaryNotifier
import com.sleeppulse.app.testutil.FakeWidgetRefresher
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepSessionFinalizerTest {

    private fun night(date: LocalDate, hrv: Double = 50.0, hr: Int = 60) = NightlySummary(
        date = date,
        sleepScore = 70,
        avgHeartRateBpm = hr,
        avgHrvMillis = hrv,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `notifies and refreshes the widget when a summary was recorded`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()
        val summary = night(LocalDate.of(2026, 8, 4))
        repository.nextDisconnectSummary = summary
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize()

        assertEquals(listOf(summary), notifier.notifiedSummaries)
        assertEquals(1, widgetRefresher.refreshCallCount)
    }

    @Test
    fun `does nothing when disconnectSensor returns null`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()
        repository.nextDisconnectSummary = null
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize()

        assertEquals(0, notifier.notifiedSummaries.size)
        assertEquals(0, widgetRefresher.refreshCallCount)
    }

    @Test
    fun `computes recovery against the just-recorded summary as last night`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()

        // 3 baseline nights already on record before tonight's summary is recorded.
        repository.nightsFlow.value = listOf(
            night(LocalDate.of(2026, 8, 1)),
            night(LocalDate.of(2026, 8, 2)),
            night(LocalDate.of(2026, 8, 3)),
        )
        val tonight = night(LocalDate.of(2026, 8, 4), hrv = 62.5, hr = 54) // well-recovered vs. baseline
        repository.nextDisconnectSummary = tonight
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize()

        // FakeSleepRepository.disconnectSensor() prepends nextDisconnectSummary to nightsFlow
        // itself (mirroring the real repository's recordNightlySummary), so recentNights()
        // already includes tonight as "last night" by the time recovery is computed.
        assertEquals(com.sleeppulse.app.ui.dashboard.RecoveryTier.OPTIMAL, notifier.notifiedRecoveryResults.single()?.tier)
    }

    @Test
    fun `a repository failure during finalize does not throw`() = runTest {
        val repository = object : com.sleeppulse.app.data.repository.SleepRepository by FakeSleepRepository() {
            override suspend fun disconnectSensor(): NightlySummary? = error("simulated persistence failure")
        }
        val notifier = FakeSleepSummaryNotifier()
        val widgetRefresher = FakeWidgetRefresher()
        val finalizer = SleepSessionFinalizer(repository, notifier, widgetRefresher)

        finalizer.finalize() // must not throw

        assertEquals(0, notifier.notifiedSummaries.size)
        assertEquals(0, widgetRefresher.refreshCallCount)
    }
}
