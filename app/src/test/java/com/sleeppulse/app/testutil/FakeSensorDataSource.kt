package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.source.SensorDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSensorDataSource : SensorDataSource {
    val connectionStateFlow = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    val readingsFlow = MutableSharedFlow<SensorReading>(extraBufferCapacity = 10)

    var connectCallCount = 0
        private set
    var disconnectCallCount = 0
        private set

    override val connectionState: Flow<SensorConnectionState> = connectionStateFlow

    override fun readings(): Flow<SensorReading> = readingsFlow

    override suspend fun connect() {
        connectCallCount++
        connectionStateFlow.value = SensorConnectionState.Connected(deviceName = "fake-device")
    }

    override suspend fun disconnect() {
        disconnectCallCount++
        connectionStateFlow.value = SensorConnectionState.Disconnected
    }
}
