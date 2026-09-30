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
import com.sleeppulse.app.data.model.StageSegment
import com.sleeppulse.app.data.source.SensorDataSource
import com.sleeppulse.app.tracking.HealthConnectManager
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val FLUSH_BATCH_SIZE = 20
private const val FLUSH_INTERVAL_MILLIS = 30_000L

/**
 * One night is stored per date, so a second session that day (a nap, a short test) must not
 * overwrite a real night: the longer session's stats win, the newer one on a tie. Tags from
 * both are kept — built summaries carry none, and the user's tags belong to the date.
 */
// ponytail: read-then-write isn't atomic; a tag edit landing in between could be lost. Move into
// a @Transaction DAO method if that ever matters.
internal fun nightToKeep(existing: NightlySummary?, new: NightlySummary): NightlySummary {
    val winner = if (existing != null && existing.totalSleepMinutes > new.totalSleepMinutes) existing else new
    return winner.copy(tags = (existing?.tags.orEmpty() + new.tags).distinct())
}

class SleepRepositoryImpl @Inject constructor(
    private val sensorDataSource: SensorDataSource,
    private val dao: NightlySummaryDao,
    private val sessionDao: SleepSessionDao,
    private val appScope: CoroutineScope,
    private val healthConnectManager: HealthConnectManager,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SleepRepository {

    override val connectionState: Flow<SensorConnectionState> = sensorDataSource.connectionState

    override fun liveReadings(): Flow<SensorReading> = sensorDataSource.readings()

    override fun recentNights(): Flow<List<NightlySummary>> =
        dao.observeRecent().map { entities -> entities.map { it.toDomain() } }

    private var activeSessionId: Long? = null
    private var activeSessionStartMillis: Long? = null
    private var collectionJob: Job? = null
    private var flushTimerJob: Job? = null
    private val pendingReadingsMutex = Mutex()
    private val pendingReadings = mutableListOf<SessionReadingEntity>()

    // Guards the capture-and-null of activeSessionId/activeSessionStartMillis in
    // disconnectSensor() so two concurrent callers (e.g. a stop-action and onDestroy racing,
    // or a rapid double-tap) can't both observe a non-null activeSessionId and both proceed to
    // finalizeSession with the same id, risking a duplicate Health Connect write.
    private val activeSessionMutex = Mutex()

    init {
        appScope.launch { recoverUnfinalizedSessions() }
    }

    override suspend fun connectSensor() {
        sensorDataSource.connect()
        val startMillis = nowMillis()
        val sessionId = sessionDao.createSession(
            SleepSessionEntity(startEpochMillis = startMillis, finalized = false)
        )
        activeSessionId = sessionId
        activeSessionStartMillis = startMillis

        collectionJob = appScope.launch {
            sensorDataSource.readings().collect { reading ->
                val shouldFlush = pendingReadingsMutex.withLock {
                    pendingReadings.add(reading.toSessionEntity(sessionId))
                    pendingReadings.size >= FLUSH_BATCH_SIZE
                }
                if (shouldFlush) flush()
            }
        }
        flushTimerJob = appScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                flush()
            }
        }
    }

    override suspend fun disconnectSensor(): NightlySummary? {
        collectionJob?.cancelAndJoin()
        flushTimerJob?.cancelAndJoin()
        collectionJob = null
        flushTimerJob = null
        flush()
        val (sessionId, startMillis) = activeSessionMutex.withLock {
            val id = activeSessionId
            val start = activeSessionStartMillis
            activeSessionId = null
            activeSessionStartMillis = null
            id to start
        }
        sensorDataSource.disconnect()
        return if (sessionId != null && startMillis != null) {
            finalizeSession(sessionId, startMillis)
        } else {
            null
        }
    }

    private suspend fun flush() {
        val toInsert = pendingReadingsMutex.withLock {
            if (pendingReadings.isEmpty()) return@withLock null
            val snapshot = pendingReadings.toList()
            pendingReadings.clear()
            snapshot
        }
        if (toInsert != null) sessionDao.insertReadings(toInsert)
    }

    override suspend fun recordNightlySummary(summary: NightlySummary) =
        record(summary, emptyList())

    private suspend fun record(
        summary: NightlySummary,
        stages: List<StageSegment>,
        readings: List<SensorReading> = emptyList(),
    ) {
        dao.upsert(nightToKeep(dao.getByDate(summary.date.toEpochDay())?.toDomain(), summary).toEntity())
        dao.trimToLast30Days()
        healthConnectManager.writeSleepSession(summary, stages)
        if (readings.isNotEmpty()) healthConnectManager.writeHeartRate(readings)
    }

    override suspend fun updateTags(date: LocalDate, tags: List<String>) {
        dao.updateTags(date.toEpochDay(), tags)
    }

    suspend fun recoverUnfinalizedSessions() {
        sessionDao.unfinalizedSessions().forEach { session ->
            try {
                finalizeSession(session.sessionId, session.startEpochMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave this session unfinalized so it's retried on the next launch rather than
                // silently discarding real reading data from a single bad session.
                android.util.Log.w("SleepPulse", "Session finalize failed; will retry on next launch", e)
            }
        }
    }

    /**
     * Reads back a session's persisted readings, records a [NightlySummary] if there are any,
     * then finalizes/clears the session row. Only finalizes on success — if [recordNightlySummary]
     * throws (e.g. a Room or Health Connect write failure), the session is left unfinalized so
     * it's retried by [recoverUnfinalizedSessions] on next app launch rather than losing data.
     */
    private suspend fun finalizeSession(sessionId: Long, startEpochMillis: Long): NightlySummary? {
        val readings = sessionDao.readingsFor(sessionId).map { it.toDomainReading() }
        if (readings.isEmpty()) {
            sessionDao.finalizeAndClear(sessionId)
            return null
        }
        val date = java.time.Instant.ofEpochMilli(startEpochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val summary = NightSummaryBuilder.build(readings, date)
        record(summary, NightSummaryBuilder.segments(readings), readings)
        sessionDao.finalizeAndClear(sessionId)
        return summary
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
        bedtimeEpochMillis = bedtimeEpochMillis,
        sleepScore = sleepScore,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
        tags = tags,
    )

    private fun NightlySummary.toEntity() = NightlySummaryEntity(
        dateEpochDay = date.toEpochDay(),
        bedtimeEpochMillis = bedtimeEpochMillis,
        sleepScore = sleepScore,
        avgHeartRateBpm = avgHeartRateBpm,
        avgHrvMillis = avgHrvMillis,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
        tags = tags,
    )
}
