package com.sleeppulse.app.ui.dashboard

import app.cash.turbine.test
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.testutil.FakeSleepRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

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
        val viewModel = DashboardViewModel(repository)

        viewModel.state.test {
            assertEquals(DashboardState(), awaitItem())

            viewModel.onIntent(DashboardIntent.Start)
            advanceUntilIdle()

            // The connectionState collector's initial emission reflects Disconnected
            // (the state before the fake's connectSensor() flips it to Connected).
            val afterStart = awaitItem()
            assertEquals(SensorConnectionState.Disconnected, afterStart.connectionState)
            assertEquals(false, afterStart.isLoading)

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
        val viewModel = DashboardViewModel(repository)

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
        val viewModel = DashboardViewModel(repository)
        viewModel.onIntent(DashboardIntent.Start)
        advanceUntilIdle()
        repository.connectionStateFlow.value = SensorConnectionState.Connected("fake-device")
        advanceUntilIdle()

        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(1, repository.disconnectSensorCallCount)
    }

    @Test
    fun `ToggleSensorConnection connects when not connected`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

        // No Start() call: state stays at its default (Disconnected), so the toggle
        // must treat the sensor as not connected and call connectSensor().
        viewModel.onIntent(DashboardIntent.ToggleSensorConnection)
        advanceUntilIdle()

        assertEquals(1, repository.connectSensorCallCount)
    }

    @Test
    fun `wind-down flow advances through all steps and stops at DONE`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

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
        val viewModel = DashboardViewModel(repository)

        viewModel.onIntent(DashboardIntent.BeginWindDown)
        viewModel.onIntent(DashboardIntent.AdvanceWindDownStep)
        assertEquals(WindDownStep.DIM_LIGHTS, viewModel.state.value.windDownStep)

        viewModel.onIntent(DashboardIntent.CancelWindDown)
        assertNull(viewModel.state.value.windDownStep)
    }

    @Test
    fun `disconnecting after readings accumulated records a nightly summary`() = runTest {
        val repository = FakeSleepRepository()
        val viewModel = DashboardViewModel(repository)

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
        val viewModel = DashboardViewModel(repository)

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
        val viewModel = DashboardViewModel(repository)

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
        val viewModel = DashboardViewModel(repository)

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
}
