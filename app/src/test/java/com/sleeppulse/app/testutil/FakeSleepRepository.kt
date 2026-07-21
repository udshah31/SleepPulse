package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSleepRepository : SleepRepository {
    val connectionStateFlow = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    val readingsFlow = MutableSharedFlow<SensorReading>(extraBufferCapacity = 10)
    val nightsFlow = MutableStateFlow<List<NightlySummary>>(emptyList())

    var connectSensorCallCount = 0
        private set
    var disconnectSensorCallCount = 0
        private set
    val recordedSummaries = mutableListOf<NightlySummary>()

    override val connectionState: Flow<SensorConnectionState> = connectionStateFlow

    override fun liveReadings(): Flow<SensorReading> = readingsFlow

    override fun recentNights(): Flow<List<NightlySummary>> = nightsFlow

    override suspend fun connectSensor() {
        connectSensorCallCount++
        connectionStateFlow.value = SensorConnectionState.Connected(deviceName = "fake-device")
    }

    override suspend fun disconnectSensor() {
        disconnectSensorCallCount++
        connectionStateFlow.value = SensorConnectionState.Disconnected
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) {
        recordedSummaries.add(summary)
    }

    override suspend fun updateTags(date: java.time.LocalDate, tags: List<String>) {
        val index = recordedSummaries.indexOfFirst { it.date == date }
        if (index != -1) {
            recordedSummaries[index] = recordedSummaries[index].copy(tags = tags)
            nightsFlow.value = recordedSummaries.toList()
        }
    }
}
