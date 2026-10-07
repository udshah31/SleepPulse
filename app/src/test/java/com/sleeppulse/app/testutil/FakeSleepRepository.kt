package com.sleeppulse.app.testutil

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import com.sleeppulse.shared.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSleepRepository : SleepRepository {
    val connectionStateFlow = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    val readingsFlow = MutableSharedFlow<SensorReading>(extraBufferCapacity = 10)
    val nightsFlow = MutableStateFlow<List<NightlySummary>>(emptyList())
    var liveReadingsCallCount = 0
        private set
    var recentNightsCallCount = 0
        private set

    var connectSensorCallCount = 0
        private set
    var disconnectSensorCallCount = 0
        private set
    val recordedSummaries = mutableListOf<NightlySummary>()
    var nextDisconnectSummary: NightlySummary? = null

    override val connectionState: Flow<SensorConnectionState> = connectionStateFlow
    val isTrackingFlow = MutableStateFlow(false)
    override val isTracking: StateFlow<Boolean> = isTrackingFlow

    override fun liveReadings(): Flow<SensorReading> {
        liveReadingsCallCount++
        return readingsFlow
    }

    override fun recentNights(): Flow<List<NightlySummary>> {
        recentNightsCallCount++
        return nightsFlow
    }

    override suspend fun connectSensor() {
        connectSensorCallCount++
        isTrackingFlow.value = true
        connectionStateFlow.value = SensorConnectionState.Connected(deviceName = "fake-device")
    }

    override suspend fun disconnectSensor(): NightlySummary? {
        disconnectSensorCallCount++
        isTrackingFlow.value = false
        connectionStateFlow.value = SensorConnectionState.Disconnected
        val summary = nextDisconnectSummary
        if (summary != null) {
            // Mirrors recordNightlySummary()'s effect on nightsFlow below, so callers that
            // read recentNights() right after disconnecting (e.g. to compute recovery) see
            // tonight's summary already reflected, matching the real repository.
            nightsFlow.value = listOf(summary) + nightsFlow.value
        }
        return summary
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) {
        recordedSummaries.add(summary)
        nightsFlow.value = listOf(summary) + nightsFlow.value
    }

    override suspend fun updateTags(date: kotlinx.datetime.LocalDate, tags: List<String>) {
        val index = recordedSummaries.indexOfFirst { it.date == date }
        if (index != -1) {
            recordedSummaries[index] = recordedSummaries[index].copy(tags = tags)
            nightsFlow.value = recordedSummaries.toList()
        }
    }
}
