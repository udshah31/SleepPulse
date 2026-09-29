package com.sleeppulse.app.tracking

import androidx.health.connect.client.records.SleepSessionRecord
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.data.model.StageSegment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HealthConnectManagerTest {

    private val zone = ZoneId.of("America/Chicago")
    private val start = 1_780_000_000_000L

    private fun summary(bedtime: Long = start, minutes: Int = 60) = NightlySummary(
        date = LocalDate.of(2026, 7, 18),
        bedtimeEpochMillis = bedtime,
        sleepScore = 80,
        avgHeartRateBpm = 55,
        avgHrvMillis = 60.0,
        totalSleepMinutes = minutes,
        deepSleepMinutes = 0,
        remSleepMinutes = 0,
    )

    @Test
    fun `record spans first to last segment with mapped stages, zone offsets and a stable client id`() {
        val segments = listOf(
            StageSegment(start, start + 600_000, SleepStage.LIGHT),
            StageSegment(start + 600_000, start + 1_800_000, SleepStage.DEEP),
        )

        val record = HealthConnectManager.buildSleepRecord(summary(), segments, zone)!!

        assertEquals(Instant.ofEpochMilli(start), record.startTime)
        assertEquals(Instant.ofEpochMilli(start + 1_800_000), record.endTime)
        assertEquals(zone.rules.getOffset(Instant.ofEpochMilli(start)), record.startZoneOffset)
        assertEquals(
            listOf(SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_DEEP),
            record.stages.map { it.stage },
        )
        assertEquals("sleeppulse-$start", record.metadata.clientRecordId)
    }

    @Test
    fun `without segments the end falls back to start plus total minutes`() {
        val record = HealthConnectManager.buildSleepRecord(summary(minutes = 45), emptyList(), zone)!!

        assertEquals(Instant.ofEpochMilli(start + 45 * 60_000L), record.endTime)
        assertEquals(emptyList<SleepSessionRecord.Stage>(), record.stages)
    }

    @Test
    fun `zero duration or unknown bedtime produces no record instead of a rejected insert`() {
        assertNull(HealthConnectManager.buildSleepRecord(summary(minutes = 0), emptyList(), zone))
        assertNull(HealthConnectManager.buildSleepRecord(summary(bedtime = 0L), emptyList(), zone))
    }

    @Test
    fun `a stage that spills outside the session is clamped and a fully outside one is dropped`() {
        val segments = listOf(
            StageSegment(start - 5_000, start + 10_000, SleepStage.LIGHT),
            StageSegment(start + 10_000, start + 20_000, SleepStage.REM),
        )

        val record = HealthConnectManager.buildSleepRecord(summary(), segments, zone)!!

        assertEquals(Instant.ofEpochMilli(start), record.stages.first().startTime)
        assertEquals(2, record.stages.size)
        assertNotNull(record.metadata)
        assertEquals(ZoneOffset.ofHours(-5), record.startZoneOffset)
    }
}
