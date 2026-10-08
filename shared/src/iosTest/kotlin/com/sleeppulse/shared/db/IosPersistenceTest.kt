package com.sleeppulse.shared.db

import androidx.room.useWriterConnection
import com.sleeppulse.shared.model.*
import com.sleeppulse.shared.repository.RoomSessionStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosPersistenceTest {
    private val start = 1_700_000_000_000L

    @Test fun `unfinished session survives reopen and recovery is idempotent`() = runTest {
        val path = NSTemporaryDirectory() + "sleeppulse-${NSUUID().UUIDString}.db"
        var db = IosDatabaseFactory.open(path)
        try {
            val storage = RoomSessionStorage(db)
            val session = storage.create(start)
            storage.append(session.sessionId, SensorReading(start, 60, null, SleepStage.DEEP))
            storage.append(session.sessionId, SensorReading(start + 65_000, 62, null, SleepStage.REM))
            db.close()
            db = IosDatabaseFactory.open(path)
            val reopened = RoomSessionStorage(db)
            assertEquals(session, reopened.unfinished().single())
            val summary = reopened.finish(session)
            assertEquals(1, summary?.totalSleepMinutes)
            assertNull(summary?.avgHrvMillis)
            assertNull(reopened.finish(session))
            assertTrue(reopened.unfinished().isEmpty())
            assertTrue(db.sleepSessionDao().readingsFor(session.sessionId).isEmpty())
            db.close()
            db = IosDatabaseFactory.open(path)
            val saved = RoomSessionStorage(db).recentNights().first().single()
            assertEquals(summary, saved)
            assertEquals(localDateAt(start), saved.date)
        } finally { db.close(); removeDatabase(path) }
    }

    @Test fun `aborted finalization rolls back summary and keeps raw readings`() = runTest {
        val path = NSTemporaryDirectory() + "sleeppulse-${NSUUID().UUIDString}.db"
        val db = IosDatabaseFactory.open(path)
        try {
            val storage = RoomSessionStorage(db)
            val session = storage.create(start)
            storage.append(session.sessionId, SensorReading(start, 60, 65.0, SleepStage.LIGHT))
            db.useWriterConnection { connection ->
                connection.usePrepared("CREATE TRIGGER refuse_finalization BEFORE UPDATE ON sleep_session " +
                    "BEGIN SELECT RAISE(ABORT, 'injected failure'); END") { it.step() }
            }
            assertFails { storage.finish(session) }
            assertTrue(storage.recentNights().first().isEmpty())
            assertEquals(session, storage.unfinished().single())
            assertEquals(1, db.sleepSessionDao().readingsFor(session.sessionId).size)
            db.useWriterConnection { it.usePrepared("DROP TRIGGER refuse_finalization") { statement -> statement.step() } }
            assertNotNull(storage.finish(session))
            assertTrue(storage.unfinished().isEmpty())
        } finally { db.close(); removeDatabase(path) }
    }

    @Test fun `history retains newest thirty dates and nullable metrics and tags`() = runTest {
        val path = NSTemporaryDirectory() + "sleeppulse-${NSUUID().UUIDString}.db"
        val db = IosDatabaseFactory.open(path)
        try {
            val storage = RoomSessionStorage(db)
            for (day in 0..34) storage.record(NightlySummary(LocalDate.fromEpochDays(20_000 + day),
                sleepScore = 80, avgHeartRateBpm = 60, avgHrvMillis = null,
                totalSleepMinutes = 100, deepSleepMinutes = 30, remSleepMinutes = 20,
                tags = listOf("Exercise")))
            val recent = storage.recentNights().first()
            assertEquals(30, recent.size)
            assertEquals(20_034, recent.first().date.toEpochDays())
            assertEquals(20_005, recent.last().date.toEpochDays())
            storage.record(recent.first().copy(sleepScore = 30, totalSleepMinutes = 1, tags = listOf("Coffee")))
            val kept = storage.recentNights().first().first()
            assertEquals(80, kept.sleepScore)
            assertEquals(listOf("Exercise", "Coffee"), kept.tags)
            assertNull(kept.avgHrvMillis)
            db.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA user_version") {
                    assertTrue(it.step()); assertEquals(5L, it.getLong(0))
                }
            }
        } finally { db.close(); removeDatabase(path) }
    }

    private fun removeDatabase(path: String) {
        listOf(path, "$path-wal", "$path-shm").forEach {
            NSFileManager.defaultManager.removeItemAtPath(it, null)
        }
    }
}
