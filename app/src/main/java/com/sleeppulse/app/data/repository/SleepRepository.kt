package com.sleeppulse.app.data.repository

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Sits between whichever [com.sleeppulse.app.data.source.SensorDataSource] is bound (simulated
 * or BLE) and Room's local cache on one side, and the ViewModel layer on the other. The
 * ViewModel never talks to a data source or DAO directly.
 */
interface SleepRepository {
    val connectionState: Flow<SensorConnectionState>

    fun liveReadings(): Flow<SensorReading>

    fun recentNights(): Flow<List<NightlySummary>>

    suspend fun connectSensor()

    suspend fun disconnectSensor()

    suspend fun recordNightlySummary(summary: NightlySummary)

    suspend fun updateTags(date: LocalDate, tags: List<String>)
}
