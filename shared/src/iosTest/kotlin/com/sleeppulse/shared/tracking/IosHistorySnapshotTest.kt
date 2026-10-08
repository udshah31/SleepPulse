package com.sleeppulse.shared.tracking

import com.sleeppulse.shared.db.IosDatabaseFactory
import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.repository.RoomSessionStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import platform.Foundation.*
import kotlin.test.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosHistorySnapshotTest {
    private fun night(age: Int, hr: Int = 60, hrv: Double? = 50.0, minutes: Int = 480) =
        NightlySummary(LocalDate.fromEpochDays(20_000 - age),
            bedtimeEpochMillis = (20_000L - age) * 86_400_000 + 22 * 3_600_000,
            sleepScore = 80, avgHeartRateBpm = hr, avgHrvMillis = hrv,
            totalSleepMinutes = minutes, deepSleepMinutes = 90, remSleepMinutes = 100)

    @Test fun `primitive snapshot preserves date windows and missing HRV`() {
        val saved = IosHistorySnapshotBuilder.build(listOf(night(0, 54, null)) + List(3) { night(it + 1, hrv = null) })
        assertEquals(4, saved.insights.recordedNights)
        assertEquals(3, saved.insights.baselineNights)
        assertEquals(saved.nights.first().isoDate, saved.insights.latestIsoDate)
        assertEquals(70, saved.insights.recovery?.score)
        assertEquals("ADEQUATE", saved.insights.recovery?.tier)
        assertNull(saved.insights.recovery?.hrvDeviation)
        assertEquals(0.1, saved.insights.recovery!!.heartRateDeviation, 0.00001)
        assertEquals(0, saved.insights.debt?.deficitMinutes)
        assertEquals(480, saved.insights.debt?.targetMinutes)
        assertEquals(100, saved.insights.consistencyScore)
        assertTrue(saved.nights.all { it.averageHrv == null })
        assertNull(saved.insights.scoreChanges.last().direction)
        assertEquals("FLAT", saved.insights.scoreChanges.first().direction)
        assertNull(IosHistorySnapshotBuilder.build(emptyList()).insights.latestIsoDate)
    }

    @Test fun `Room update and reopen preserve coherent nights and calculated insights`() = runTest {
        val path = NSTemporaryDirectory() + "insights-${NSUUID().UUIDString}.db"
        var db = IosDatabaseFactory.open(path)
        try {
            var storage = RoomSessionStorage(db)
            for (age in 3 downTo 1) storage.record(night(age))
            val partial = IosHistorySnapshotBuilder.build(storage.recentNights().first())
            assertEquals(3, partial.nights.size)
            assertNull(partial.insights.recovery)
            storage.record(night(0, 54, 62.5))
            val full = IosHistorySnapshotBuilder.build(storage.recentNights().first())
            assertEquals(4, full.nights.size)
            assertEquals(full.nights.size, full.insights.recordedNights)
            assertEquals(88, full.insights.recovery?.score)
            assertEquals(full.nights.first().isoDate, full.insights.latestIsoDate)
            storage.record(night(0, 100, null, 1)) // Short same-date test cannot replace longer statistics.
            assertEquals(full, IosHistorySnapshotBuilder.build(storage.recentNights().first()))
            db.close()
            db = IosDatabaseFactory.open(path)
            storage = RoomSessionStorage(db)
            assertEquals(full, IosHistorySnapshotBuilder.build(storage.recentNights().first()))
        } finally {
            db.close()
            listOf(path, "$path-wal", "$path-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        }
    }
}
