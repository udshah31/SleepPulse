package com.sleeppulse.app.data.repository

import app.cash.turbine.test
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.testutil.FakeNightlySummaryDao
import com.sleeppulse.app.testutil.FakeSensorDataSource
import com.sleeppulse.app.testutil.FakeSleepSessionDao
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class SleepRepositoryImplTest {

    private fun summary(date: LocalDate, score: Int) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 58,
        avgHrvMillis = 72.5,
        totalSleepMinutes = 410,
        deepSleepMinutes = 95,
        remSleepMinutes = 105,
    )

    private fun reading(timestampMillis: Long, heartRateBpm: Int = 60) = SensorReading(
        timestampMillis = timestampMillis,
        heartRateBpm = heartRateBpm,
        hrvMillis = 60.0,
        sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `recordNightlySummary upserts then trims`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        val date = LocalDate.of(2026, 7, 17)
        repository.recordNightlySummary(summary(date, score = 88))

        val stored = dao.entitiesFlow.value.single()
        assertEquals(date.toEpochDay(), stored.dateEpochDay)
        assertEquals(88, stored.sleepScore)
        assertEquals(58, stored.avgHeartRateBpm)
        assertEquals(72.5, stored.avgHrvMillis, 0.0001)
        assertEquals(410, stored.totalSleepMinutes)
        assertEquals(95, stored.deepSleepMinutes)
        assertEquals(105, stored.remSleepMinutes)
        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
    }

    @Test
    fun `recentNights maps entities to domain objects with date round-trip`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        val date = LocalDate.of(2026, 7, 10)
        dao.entitiesFlow.value = listOf(
            NightlySummaryEntity(
                dateEpochDay = date.toEpochDay(),
                sleepScore = 72,
                avgHeartRateBpm = 61,
                avgHrvMillis = 65.0,
                totalSleepMinutes = 400,
                deepSleepMinutes = 80,
                remSleepMinutes = 90,
            )
        )

        repository.recentNights().test {
            val nights = awaitItem()
            assertEquals(1, nights.size)
            assertEquals(date, nights[0].date)
            assertEquals(72, nights[0].sleepScore)
            assertEquals(61, nights[0].avgHeartRateBpm)
            assertEquals(65.0, nights[0].avgHrvMillis, 0.0001)
            assertEquals(400, nights[0].totalSleepMinutes)
            assertEquals(80, nights[0].deepSleepMinutes)
            assertEquals(90, nights[0].remSleepMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connectSensor creates a session and disconnectSensor delegates to the data source`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 1_000L }

        repository.connectSensor()
        assertEquals(1, sensorDataSource.connectCallCount)
        assertEquals(1, sessionDao.sessions.size)
        assertEquals(1_000L, sessionDao.sessions.single().startEpochMillis)
        assertTrue(!sessionDao.sessions.single().finalized)

        repository.disconnectSensor()
        assertEquals(1, sensorDataSource.disconnectCallCount)
    }

    @Test
    fun `readings flush to the session dao every 20 readings`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.connectSensor()
        runCurrent()
        repeat(20) { i -> sensorDataSource.readingsFlow.emit(reading(timestampMillis = i.toLong())) }
        advanceTimeBy(1)

        assertEquals(20, sessionDao.readings.size)
    }

    @Test
    fun `readings flush after 30 seconds even under the batch size`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.connectSensor()
        runCurrent()
        sensorDataSource.readingsFlow.emit(reading(timestampMillis = 1L))
        sensorDataSource.readingsFlow.emit(reading(timestampMillis = 2L))
        assertEquals(0, sessionDao.readings.size)

        advanceTimeBy(30_001)

        assertEquals(2, sessionDao.readings.size)
    }

    @Test
    fun `liveReadings and connectionState pass through from the data source`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.connectSensor()
        assertEquals(1, sensorDataSource.connectCallCount)

        repository.disconnectSensor()
        assertEquals(1, sensorDataSource.disconnectCallCount)

        repository.liveReadings().test {
            sensorDataSource.readingsFlow.emit(reading(timestampMillis = 1L, heartRateBpm = 62))
            val received = awaitItem()
            assertEquals(62, received.heartRateBpm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnecting with no readings collected does not call insertReadings`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.connectSensor()
        repository.disconnectSensor()

        assertTrue(sessionDao.recordedCalls.none { it.startsWith("insertReadings") })
        assertTrue(sessionDao.sessions.single().finalized)
        assertEquals(0, sessionDao.readings.size)
    }

    @Test
    fun `disconnecting after readings were flushed records a nightly summary dated from session start`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = LocalDate.of(2026, 7, 20).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        repository.connectSensor()
        runCurrent()
        repeat(20) { i -> sensorDataSource.readingsFlow.emit(reading(timestampMillis = startMillis + i * 60_000L)) }
        advanceTimeBy(1)

        val summary = repository.disconnectSensor()

        assertEquals(LocalDate.of(2026, 7, 20), summary?.date)
        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
        assertTrue(sessionDao.sessions.single().finalized)
        assertEquals(0, sessionDao.readings.size)
    }

    @Test
    fun `disconnecting with no readings returns null and still finalizes the empty session`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.connectSensor()
        val summary = repository.disconnectSensor()

        assertEquals(null, summary)
        assertTrue(dao.recordedCalls.isEmpty())
        assertTrue(sessionDao.sessions.single().finalized)
    }

    @Test
    fun `a recordNightlySummary failure during disconnect leaves the session unfinalized`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = 1_000L
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        // Deliberately never call runCurrent()/advanceTimeBy()/advanceUntilIdle() before
        // disconnectSensor(): doing so would let SleepRepositoryImpl's init-launched
        // recoverUnfinalizedSessions() (queued on backgroundScope at construction time) race
        // ahead and finalize this session as "empty" before the failure scenario below runs.
        // connectSensor() and disconnectSensor() are called directly (suspend, not launched),
        // so their own logic runs without draining that queued background coroutine.
        repository.connectSensor()
        val sessionId = sessionDao.sessions.single().sessionId
        sessionDao.readingsForFailures.add(sessionId)

        try {
            repository.disconnectSensor()
        } catch (e: IllegalStateException) {
            // expected: readingsFor throws for this session id
        }

        assertTrue(!sessionDao.sessions.single().finalized)
    }

    @Test
    fun `recoverUnfinalizedSessions rebuilds and records a summary for a leftover session`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = LocalDate.of(2026, 7, 18).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        sessionDao.sessions.add(
            com.sleeppulse.app.data.local.SleepSessionEntity(
                sessionId = 5L,
                startEpochMillis = startMillis,
                finalized = false,
            )
        )
        sessionDao.readings.addAll(
            listOf(
                com.sleeppulse.app.data.local.SessionReadingEntity(
                    id = 1L, sessionId = 5L, timestampMillis = startMillis,
                    heartRateBpm = 58, hrvMillis = 70.0, sleepStage = SleepStage.LIGHT,
                ),
                com.sleeppulse.app.data.local.SessionReadingEntity(
                    id = 2L, sessionId = 5L, timestampMillis = startMillis + 60_000,
                    heartRateBpm = 56, hrvMillis = 72.0, sleepStage = SleepStage.DEEP,
                ),
            )
        )
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        repository.recoverUnfinalizedSessions()

        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
        val stored = dao.entitiesFlow.value.single()
        assertEquals(LocalDate.of(2026, 7, 18).toEpochDay(), stored.dateEpochDay)
        assertTrue(sessionDao.sessions.single().finalized)
        assertEquals(0, sessionDao.readings.size)
    }

    @Test
    fun `recoverUnfinalizedSessions drops an empty leftover session without recording anything`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        sessionDao.sessions.add(
            com.sleeppulse.app.data.local.SleepSessionEntity(
                sessionId = 9L, startEpochMillis = 0L, finalized = false,
            )
        )
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { 0L }

        repository.recoverUnfinalizedSessions()

        assertTrue(dao.recordedCalls.isEmpty())
        assertTrue(sessionDao.sessions.single().finalized)
    }

    @Test
    fun `recoverUnfinalizedSessions recovers a healthy session even when another session's recovery throws`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = LocalDate.of(2026, 7, 18).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        // Session 5: corrupt / unreadable — readingsFor throws for it.
        sessionDao.sessions.add(
            com.sleeppulse.app.data.local.SleepSessionEntity(
                sessionId = 5L,
                startEpochMillis = startMillis,
                finalized = false,
            )
        )
        sessionDao.readingsForFailures.add(5L)

        // Session 6: healthy leftover session with real readings.
        sessionDao.sessions.add(
            com.sleeppulse.app.data.local.SleepSessionEntity(
                sessionId = 6L,
                startEpochMillis = startMillis,
                finalized = false,
            )
        )
        sessionDao.readings.addAll(
            listOf(
                com.sleeppulse.app.data.local.SessionReadingEntity(
                    id = 1L, sessionId = 6L, timestampMillis = startMillis,
                    heartRateBpm = 58, hrvMillis = 70.0, sleepStage = SleepStage.LIGHT,
                ),
                com.sleeppulse.app.data.local.SessionReadingEntity(
                    id = 2L, sessionId = 6L, timestampMillis = startMillis + 60_000,
                    heartRateBpm = 56, hrvMillis = 72.0, sleepStage = SleepStage.DEEP,
                ),
            )
        )
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        repository.recoverUnfinalizedSessions()

        // Session 6 was recovered and finalized despite session 5 throwing.
        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
        val stored = dao.entitiesFlow.value.single()
        assertEquals(LocalDate.of(2026, 7, 18).toEpochDay(), stored.dateEpochDay)
        val session6 = sessionDao.sessions.single { it.sessionId == 6L }
        assertTrue(session6.finalized)

        // Session 5 was left unfinalized (with its data intact) so it can be retried later.
        val session5 = sessionDao.sessions.single { it.sessionId == 5L }
        assertTrue(!session5.finalized)
    }

    @Test
    fun `concurrent disconnectSensor calls only finalize the session once`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val sessionDao = FakeSleepSessionDao()
        val startMillis = LocalDate.of(2026, 7, 20).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val repository = SleepRepositoryImpl(sensorDataSource, dao, sessionDao, backgroundScope, mock()) { startMillis }

        repository.connectSensor()
        runCurrent()
        repeat(20) { i -> sensorDataSource.readingsFlow.emit(reading(timestampMillis = startMillis + i * 60_000L)) }
        advanceTimeBy(1)

        // Two callers racing to disconnect/finalize at the same time (e.g. the stop-action
        // branch and onDestroy() in SleepTrackingService, or a rapid double-tap). Only one
        // should observe the non-null activeSessionId and actually finalize/record; the other
        // must see it already cleared and no-op, per SleepRepositoryImpl's activeSessionMutex
        // guard in disconnectSensor().
        var first: NightlySummary? = null
        var second: NightlySummary? = null
        val job1 = launch { first = repository.disconnectSensor() }
        val job2 = launch { second = repository.disconnectSensor() }
        job1.join()
        job2.join()

        val results = listOf(first, second)
        assertEquals(1, results.count { it != null })
        assertEquals(listOf("upsert", "trimToLast30Days"), dao.recordedCalls)
        assertTrue(sessionDao.sessions.single().finalized)
    }
}
