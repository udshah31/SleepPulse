package com.sleeppulse.shared.repository

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface SleepRepository {
    val connectionState: Flow<SensorConnectionState>
    fun liveReadings(): Flow<SensorReading>
    fun recentNights(): Flow<List<NightlySummary>>
    suspend fun connectSensor()
    suspend fun disconnectSensor(): NightlySummary?
    suspend fun recordNightlySummary(summary: NightlySummary)
    suspend fun updateTags(date: LocalDate, tags: List<String>)
}
