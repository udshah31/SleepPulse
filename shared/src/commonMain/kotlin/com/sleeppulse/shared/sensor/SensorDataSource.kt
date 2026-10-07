package com.sleeppulse.shared.sensor

import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import kotlinx.coroutines.flow.Flow

interface SensorDataSource {
    val connectionState: Flow<SensorConnectionState>
    fun readings(): Flow<SensorReading>
    suspend fun connect()
    suspend fun disconnect()
}
