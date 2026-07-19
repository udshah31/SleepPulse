package com.sleeppulse.app.data.repository

import com.sleeppulse.app.data.NightSummaryBuilder
import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.local.SessionReadingEntity
import com.sleeppulse.app.data.local.SleepSessionDao
import com.sleeppulse.app.data.local.SleepSessionEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorConnectionState
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.source.SensorDataSource
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val FLUSH_BATCH_SIZE = 20
private const val FLUSH_INTERVAL_MILLIS = 30_000L

class SleepRepositoryImpl @Inject constructor(
    private val sensorDataSource: SensorDataSource,
    private val dao: NightlySummaryDao,
    private val sessionDao: SleepSessionDao,
    private val appScope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SleepRepository {

    override val connectionState: Flow<SensorConnectionState> = sensorDataSource.connectionState

    override fun liveReadings(): Flow<SensorReading> = sensorDataSource.readings()

    override fun recentNights(): Flow<List<NightlySummary>> =
        dao.observeRecent().map { entities -> entities.map { it.toDomain() } }

    private var activeSessionId: Long? = null
    private var collectionJob: Job? = null
    private var flushTimerJob: Job? = null
    private val pendingReadings = mutableListOf<SessionReadingEntity>()

    init {
        appScope.launch { recoverUnfinalizedSessions() }
    }

    override suspend fun connectSensor() {
        sensorDataSource.connect()
        val sessionId = sessionDao.createSession(
            SleepSessionEntity(startEpochMillis = nowMillis(), finalized = false)
        )
        activeSessionId = sessionId

        collectionJob = appScope.launch {
            sensorDataSource.readings().collect { reading ->
                pendingReadings.add(reading.toSessionEntity(sessionId))
                if (pendingReadings.size >= FLUSH_BATCH_SIZE) flush()
            }
        }
        flushTimerJob = appScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                flush()
            }
        }
    }

    override suspend fun disconnectSensor() {
        collectionJob?.cancelAndJoin()
        flushTimerJob?.cancelAndJoin()
        collectionJob = null
        flushTimerJob = null
        flush()
        activeSessionId?.let { sessionDao.finalizeAndClear(it) }
        activeSessionId = null
        sensorDataSource.disconnect()
    }

    private suspend fun flush() {
        if (pendingReadings.isEmpty()) return
        sessionDao.insertReadings(pendingReadings.toList())
        pendingReadings.clear()
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) {
        dao.upsert(summary.toEntity())
        dao.trimToLast30Days()
    }

    suspend fun recoverUnfinalizedSessions() {
        sessionDao.unfinalizedSessions().forEach { session ->
            val readings = sessionDao.readingsFor(session.sessionId).map { it.toDomainReading() }
            if (readings.isNotEmpty()) {
                val date = java.time.Instant.ofEpochMilli(session.startEpochMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                recordNightlySummary(NightSummaryBuilder.build(readings, date))
            }
            sessionDao.finalizeAndClear(session.sessionId)
        }
    }

    private fun SessionReadingEntity.toDomainReading() = SensorReading(
        timestampMillis = timestampMillis,
        heartRateBpm = heartRateBpm,
        hrvMillis = hrvMillis,
        sleepStage = sleepStage,
    )

    private fun SensorReading.toSessionEntity(sessionId: Long) = SessionReadingEntity(
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        heartRateBpm = heartRateBpm,
        hrvMillis = hrvMillis,
        sleepStage = sleepStage,
    )

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
