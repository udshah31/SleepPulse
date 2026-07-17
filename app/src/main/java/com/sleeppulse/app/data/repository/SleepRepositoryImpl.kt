package com.sleeppulse.app.data.repository

import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.source.SensorDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class SleepRepositoryImpl @Inject constructor(
    private val sensorDataSource: SensorDataSource,
    private val dao: NightlySummaryDao,
) : SleepRepository {

    override val connectionState: Flow<SensorConnectionState> = sensorDataSource.connectionState

    override fun liveReadings(): Flow<SensorReading> = sensorDataSource.readings()

    override fun recentNights(): Flow<List<NightlySummary>> =
        dao.observeRecent().map { entities -> entities.map { it.toDomain() } }

    override suspend fun connectSensor() = sensorDataSource.connect()

    override suspend fun disconnectSensor() = sensorDataSource.disconnect()

    override suspend fun recordNightlySummary(summary: NightlySummary) {
        dao.upsert(summary.toEntity())
        dao.trimToLast30Days()
    }

    private fun NightlySummaryEntity.toDomain() = NightlySummary(
        date = LocalDate.ofEpochDay(dateEpochDay),
        sleepScore = sleepScore,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
    )

    private fun NightlySummary.toEntity() = NightlySummaryEntity(
        dateEpochDay = date.toEpochDay(),
        sleepScore = sleepScore,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
    )
}
