package com.sleeppulse.app.ui.dashboard

import app.cash.turbine.test
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.FakeSleepSummaryNotifier
import com.sleeppulse.app.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import android.content.Context

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun reading(bpm: Int = 60, hrv: Double = 50.0) = SensorReading(
        timestampMillis = 0L,
        heartRateBpm = bpm,
        hrvMillis = hrv,
        sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `Start collects connection state and readings into state`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.state.test {
            assertEquals(DashboardState(), awaitItem())

            viewModel.onIntent(DashboardIntent.Start)
            advanceUntilIdle()

            // The connectionState collector's initial emission reflects Disconnected
            val afterStart = awaitItem()
            assertEquals(SensorConnectionState.Disconnected, afterStart.connectionState)
            assertEquals(false, afterStart.isLoading)

            // Simulate the service connecting the sensor
            repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
            val afterConnect = awaitItem()
            assertEquals(SensorConnectionState.Connected("fake-device"), afterConnect.connectionState)
            assertEquals(false, afterConnect.isLoading)

            val firstReading = reading(bpm = 55, hrv = 80.0)
            repository.readingsFlow.emit(firstReading)
            val afterReading = awaitItem()
            assertEquals(firstReading, afterReading.latestReading)
            assertEquals(listOf(firstReading), afterReading.recentReadings)
            assertEquals(SleepScoreCalculator.score(listOf(firstReading)), afterReading.sleepScore)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent readings cap at 40 points`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem() // current state before new emissions

            repeat(45) { i ->
                repository.readingsFlow.emit(reading(bpm = 60 + i))
                awaitItem()
            }

            assertEquals(40, viewModel.state.value.recentReadings.size)
            assertEquals(60 + 44, viewModel.state.value.recentReadings.last().heartRateBpm)
            assertEquals(60 + 5, viewModel.state.value.recentReadings.first().heartRateBpm)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleSensorConnection disconnects when connected`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)
        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        verify(context).startService(any())
    }

    @Test
    fun `ToggleSensorConnection connects when not connected`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        // No Start() call: state stays at its default (Disconnected), so the toggle
        // must treat the sensor as not connected and call connectSensor().
        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        verify(context).startForegroundService(any())
    }

    @Test
    fun `wind-down flow advances through all steps and stops at DONE`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.BeginWindDown)
        assertEquals(WindDownStep.BREATHE, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DIM_LIGHTS, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.SET_ALARM, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DONE, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertNull(viewModel.state.value.windDownStep)
    }

    @Test
    fun `CancelWindDown clears the step`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.BeginWindDown)
        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DIM_LIGHTS, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.CancelWindDown)
        assertNull(viewModel.state.value.windDownStep)
    }

    @Test
    fun `disconnecting after readings accumulated records a nightly summary`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        repository.readingsFlow.emit(reading(bpm = 58, hrv = 60.0))
        advanceUntilIdle()
        repository.readingsFlow.emit(reading(bpm = 62, hrv = 55.0))
        advanceUntilIdle()

        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(1, repository.recordedSummaries.size)
        val summary = repository.recordedSummaries.single()
        assertEquals(60, summary.avgHeartRateBpm)
        assertEquals(57.5, summary.avgHrvMillis, 0.0001)
        assertEquals(LocalDate.now(), summary.date)
    }

    @Test
    fun `disconnecting with no readings does not record a summary`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(0, repository.recordedSummaries.size)
    }

    @Test
    fun `recoveryResult is null until enough baseline nights are recorded`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        assertNull(viewModel.state.value.recoveryResult)
        assertEquals(0, viewModel.state.value.recordedNightsCount)

        val night = NightlySummary(
            date = LocalDate.now(),
            sleepScore = 70,
            avgHeartRateBpm = 60,
            avgHrvMillis = 50.0,
            totalSleepMinutes = 420,
            deepSleepMinutes = 90,
            remSleepMinutes = 100,
        )
        // Only 2 baseline nights after dropping the first (last night) entry — below the
        // RecoveryScoreCalculator minimum of 3.
        repository.nightsFlow.value = listOf(night, night, night)
        advanceUntilIdle()

        assertNull(viewModel.state.value.recoveryResult)
        assertEquals(3, viewModel.state.value.recordedNightsCount)
    }

    @Test
    fun `recoveryResult is computed once enough baseline nights exist`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        fun night(hrv: Double, hr: Int) = NightlySummary(
            date = LocalDate.now(),
            sleepScore = 70,
            avgHeartRateBpm = hr,
            avgHrvMillis = hrv,
            totalSleepMinutes = 420,
            deepSleepMinutes = 90,
            remSleepMinutes = 100,
        )

        val lastNight = night(hrv = 62.5, hr = 54) // well-recovered vs. the baseline below
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        repository.nightsFlow.value = listOf(lastNight) + baseline
        advanceUntilIdle()

        val result = viewModel.state.value.recoveryResult
        assertEquals(RecoveryTier.OPTIMAL, result?.tier)
        assertEquals(4, viewModel.state.value.recordedNightsCount)
    }

    @Test
    fun `disconnecting after readings fires notifier with the recorded summary`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        repository.readingsFlow.emit(reading(bpm = 58, hrv = 60.0))
        advanceUntilIdle()
        repository.readingsFlow.emit(reading(bpm = 62, hrv = 55.0))
        advanceUntilIdle()

        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(1, notifier.notifiedSummaries.size)
        val notified = notifier.notifiedSummaries.single()
        assertEquals(60, notified.avgHeartRateBpm)
        assertEquals(57.5, notified.avgHrvMillis, 0.0001)
    }

    @Test
    fun `disconnecting with no readings does not fire notifier`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(0, notifier.notifiedSummaries.size)
    }

    @Test
    fun `disconnecting notifies with recovery computed against the just-recorded summary, not stale state`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        fun night(hrv: Double, hr: Int) = NightlySummary(
            date = LocalDate.now().minusDays(1),
            sleepScore = 70,
            avgHeartRateBpm = hr,
            avgHrvMillis = hrv,
            totalSleepMinutes = 420,
            deepSleepMinutes = 90,
            remSleepMinutes = 100,
        )

        // Baseline present before Start, so recoveryResult would be null at disconnect time
        // if the notifier read state captured before tonight's summary was recorded.
        repository.nightsFlow.value = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        // recoveryResult is null here: only 3 baseline nights exist, no "last night" yet.
        assertNull(viewModel.state.value.recoveryResult)

        repository.readingsFlow.emit(reading(bpm = 54, hrv = 62.5)) // well-recovered reading
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        // The notifier must see recovery computed AFTER tonight's summary was recorded
        // (tonight becomes "last night", the pre-existing 3 nights become baseline) —
        // not the null value that was in _state.value.recoveryResult before disconnect.
        assertEquals(1, notifier.notifiedRecoveryResults.size)
        assertEquals(RecoveryTier.OPTIMAL, notifier.notifiedRecoveryResults.single()?.tier)
    }

    @Test
    fun `metricBaseline is null until recentNights emits`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        assertNull(viewModel.state.value.metricBaseline)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        assertNull(viewModel.state.value.metricBaseline)
    }

    @Test
    fun `metricBaseline is computed from recentNights once enough nights are recorded`() = runTest {
        val repository = FakeSleepRepository()
        val notifier = FakeSleepSummaryNotifier()
        val context: Context = mock()
        val viewModel = DashboardViewModel(context, repository, notifier)

        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()

        fun night(hrv: Double, hr: Int) = NightlySummary(
            date = LocalDate.now(),
            sleepScore = 70,
            avgHeartRateBpm = hr,
            avgHrvMillis = hrv,
            totalSleepMinutes = 420,
            deepSleepMinutes = 90,
            remSleepMinutes = 100,
        )

        repository.nightsFlow.value = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        advanceUntilIdle()

        val baseline = viewModel.state.value.metricBaseline
        assertEquals(50.0, baseline?.avgHrvMillis ?: 0.0, 0.0001)
    }
}
