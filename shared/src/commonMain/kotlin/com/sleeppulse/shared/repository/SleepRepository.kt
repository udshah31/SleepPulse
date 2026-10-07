package com.sleeppulse.shared.repository

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

interface SleepRepository {
    val connectionState: Flow<SensorConnectionState>

    /**
     * True from [connectSensor] until [disconnectSensor] — a session is running whether or not
     * the sensor has connected (a BLE strap that's out of range never does). Stopping is keyed
     * on this, not on [connectionState], so such a session can always be stopped.
     */
    val isTracking: StateFlow<Boolean>
    fun liveReadings(): Flow<SensorReading>
    fun recentNights(): Flow<List<NightlySummary>>
    suspend fun connectSensor()
    suspend fun disconnectSensor(): NightlySummary?
    suspend fun recordNightlySummary(summary: NightlySummary)
    suspend fun updateTags(date: LocalDate, tags: List<String>)
}
