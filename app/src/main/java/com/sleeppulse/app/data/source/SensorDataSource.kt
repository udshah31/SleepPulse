package com.sleeppulse.app.data.source

import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over anything that can produce live [SensorReading]s: a simulator for
 * development/demo, or a real BLE peripheral. Swappable at the repository boundary so
 * the rest of the app never depends on which implementation is active.
 */
interface SensorDataSource {
    val connectionState: Flow<SensorConnectionState>

    fun readings(): Flow<SensorReading>

    suspend fun connect()

    suspend fun disconnect()
}
