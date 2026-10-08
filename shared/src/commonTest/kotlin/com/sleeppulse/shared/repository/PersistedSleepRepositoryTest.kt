package com.sleeppulse.shared.repository

import com.sleeppulse.shared.db.SleepSessionEntity
import com.sleeppulse.shared.model.*
import com.sleeppulse.shared.sensor.SensorDataSource
import com.sleeppulse.shared.sleep.NightSummaryBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PersistedSleepRepositoryTest {
    private val start = 1_700_000_000_000L
    private fun reading(offset: Long, hrv: Double? = 60.0) =
        SensorReading(start + offset, 60, hrv, SleepStage.DEEP)

    @Test fun `recovery gates Start and duplicate commands produce one complete summary`() = runTest {
        val sensor = TestSensor()
        val storage = TestStorage()
        val repo = PersistedSleepRepository(sensor, storage, backgroundScope) { start }
        assertFailsWith<IllegalStateException> { repo.connectSensor() }
        repo.recover()
        repo.connectSensor()
        repo.connectSensor()
        assertEquals(1, storage.sessions.size)
        for (i in 0..65) { sensor.send(reading(i * 1_000L)); runCurrent() }
        assertEquals(40, repo.sessionState.value.readings.size)
        assertEquals(65L, repo.sessionState.value.elapsedSeconds)
        assertEquals(66, storage.samples.values.single().size)
        val summary = repo.disconnectSensor()
        assertEquals(1, summary?.totalSleepMinutes)
        assertEquals(1, summary?.deepSleepMinutes)
        assertFalse(repo.isTracking.value)
        assertNull(repo.disconnectSensor())
        assertEquals(1, storage.nights.value.size)
    }

    @Test fun `Stop waits for in flight insert before summarizing`() = runTest {
        val storage = TestStorage()
        val sensor = TestSensor()
        val repo = PersistedSleepRepository(sensor, storage, backgroundScope) { start }
        repo.recover()
        repo.connectSensor()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        storage.beforeAppend = { entered.complete(Unit); release.await() }
        sensor.send(reading(0, null))
        entered.await()
        assertTrue(repo.sessionState.value.readings.isEmpty())
        val stopped = async { repo.disconnectSensor() }
        runCurrent()
        assertFalse(stopped.isCompleted)
        assertTrue(storage.nights.value.isEmpty())
        release.complete(Unit)
        val summary = stopped.await()
        assertNotNull(summary)
        assertNull(summary.avgHrvMillis)
        assertEquals(0, summary.totalSleepMinutes)
    }

    @Test fun `background during session creation prevents sensor activation`() = runTest {
        val storage = TestStorage()
        val sensor = TestSensor()
        val repo = PersistedSleepRepository(sensor, storage, backgroundScope) { start }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var foreground = true
        storage.beforeCreate = { entered.complete(Unit); release.await() }
        repo.recover()
        val starting = async { repo.connectSensorIf { foreground } }
        entered.await()
        foreground = false
        release.complete(Unit)
        starting.await()
        assertEquals(0, sensor.connections)
        assertEquals(TrackingPhase.IDLE, repo.sessionState.value.phase)
        assertTrue(storage.unfinished().isEmpty())
        assertTrue(storage.nights.value.isEmpty())
    }

    @Test fun `write failure publishes no failed sample and retry preserves recorded data`() = runTest {
        val storage = TestStorage()
        val sensor = TestSensor()
        val repo = PersistedSleepRepository(sensor, storage, backgroundScope) { start }
        repo.recover(); repo.connectSensor()
        sensor.send(reading(0)); runCurrent()
        storage.beforeAppend = { error("disk full") }
        sensor.send(reading(1_000)); runCurrent()
        assertEquals(TrackingPhase.FAILED, repo.sessionState.value.phase)
        assertFalse(repo.isTracking.value)
        assertEquals(1, repo.sessionState.value.readings.size)
        assertEquals(1, storage.samples.values.single().size)
        assertFailsWith<IllegalStateException> { repo.connectSensor() }
        storage.beforeAppend = {}
        repo.recover()
        assertEquals(TrackingPhase.IDLE, repo.sessionState.value.phase)
        assertEquals(1, storage.nights.value.size)
    }

    @Test fun `backward timestamps are skipped and summary date is session start`() = runTest {
        val sensor = TestSensor()
        val storage = TestStorage()
        val repo = PersistedSleepRepository(sensor, storage, backgroundScope) { start }
        repo.recover(); repo.connectSensor()
        sensor.send(reading(0)); runCurrent()
        sensor.send(reading(-1_000)); runCurrent()
        sensor.send(reading(86_400_000)); runCurrent()
        assertEquals(2, storage.samples.values.single().size)
        assertEquals(localDateAt(start), repo.disconnectSensor()?.date)
    }

    @Test fun `recovery attempts other sessions and failure remains retryable`() = runTest {
        val storage = TestStorage()
        val first = storage.create(start)
        val second = storage.create(start + 86_400_000)
        storage.append(second.sessionId, reading(86_400_000, null))
        storage.failFinishId = first.sessionId
        val repo = PersistedSleepRepository(TestSensor(), storage, backgroundScope) { start }
        assertFailsWith<IllegalStateException> { repo.recover() }
        assertEquals(listOf(first.sessionId), storage.unfinished().map { it.sessionId })
        assertEquals(1, storage.nights.value.size)
        storage.failFinishId = null
        repo.recover(); repo.recover()
        assertTrue(storage.unfinished().isEmpty())
        assertEquals(1, storage.nights.value.size) // Empty first session never creates history.
    }

    @Test fun `longer night wins while incoming wins ties and tags merge`() {
        val old = NightlySummary(LocalDate(2026, 10, 7), sleepScore = 80, avgHeartRateBpm = 60,
            avgHrvMillis = null, totalSleepMinutes = 100, deepSleepMinutes = 30, remSleepMinutes = 20,
            tags = listOf("Exercise"))
        val short = old.copy(sleepScore = 30, totalSleepMinutes = 1, tags = listOf("Exercise", "Coffee"))
        val kept = nightToKeep(old, short)
        assertEquals(80, kept.sleepScore)
        assertEquals(listOf("Exercise", "Coffee"), kept.tags)
        assertEquals(30, nightToKeep(old, short.copy(totalSleepMinutes = 100)).sleepScore)
        assertEquals(old, old.toEntity().toDomain())
    }

    private class TestSensor : SensorDataSource {
        var connections = 0
        override val connectionState = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
        private val values = MutableSharedFlow<SensorReading>(extraBufferCapacity = 100)
        override fun readings() = values
        override suspend fun connect() { connections++; connectionState.value = SensorConnectionState.Connected("Test") }
        override suspend fun disconnect() { connectionState.value = SensorConnectionState.Disconnected }
        suspend fun send(reading: SensorReading) { values.emit(reading) }
    }

    private class TestStorage : SessionStorage {
        val sessions = mutableListOf<SleepSessionEntity>()
        val samples = mutableMapOf<Long, MutableList<SensorReading>>()
        val nights = MutableStateFlow<List<NightlySummary>>(emptyList())
        var beforeAppend: suspend () -> Unit = {}
        var beforeCreate: suspend () -> Unit = {}
        var failFinishId: Long? = null
        override fun recentNights() = nights
        override suspend fun create(startMillis: Long): SleepSessionEntity {
            beforeCreate()
            val session = SleepSessionEntity(sessions.size + 1L, startMillis, false)
            sessions.add(session); samples[session.sessionId] = mutableListOf()
            return session
        }
        override suspend fun append(sessionId: Long, reading: SensorReading) {
            beforeAppend(); samples.getValue(sessionId).add(reading)
        }
        override suspend fun unfinished() = sessions.filterNot { it.finalized }
        override suspend fun finish(session: SleepSessionEntity): NightlySummary? {
            check(session.sessionId != failFinishId) { "save refused" }
            val summary = samples.getValue(session.sessionId).takeIf { it.isNotEmpty() }
                ?.let { NightSummaryBuilder.build(it, localDateAt(session.startEpochMillis)) }
            if (summary != null) record(summary)
            sessions[sessions.indexOfFirst { it.sessionId == session.sessionId }] = session.copy(finalized = true)
            samples.getValue(session.sessionId).clear()
            return summary
        }
        override suspend fun record(summary: NightlySummary) { nights.value = nights.value + summary }
        override suspend fun updateTags(date: LocalDate, tags: List<String>) {}
    }
}
