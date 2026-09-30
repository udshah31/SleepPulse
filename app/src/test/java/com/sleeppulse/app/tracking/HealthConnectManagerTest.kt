package com.sleeppulse.app.tracking

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
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
        assertEquals(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, record.metadata.recordingMethod)
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

    private val originOf = { r: SleepSessionRecord -> r.metadata.clientRecordId!! }

    private fun hcRecord(pkg: String, stages: List<SleepSessionRecord.Stage> = emptyList()) = SleepSessionRecord(
        startTime = Instant.ofEpochMilli(start),
        startZoneOffset = null,
        endTime = Instant.ofEpochMilli(start + 8 * 3_600_000L),
        endZoneOffset = null,
        stages = stages,
        // Carries the fake origin; see originOf below.
        metadata = Metadata.autoRecorded(Device(type = Device.TYPE_UNKNOWN), clientRecordId = pkg, clientRecordVersion = 0L),
    )

    @Test
    fun `fromOtherApps drops our own records and sums deep and rem minutes from stages`() {
        val t = { min: Long -> Instant.ofEpochMilli(start + min * 60_000L) }
        val records = listOf(
            hcRecord("com.sleeppulse.app"),
            hcRecord(
                "com.samsung.android.wear.shealth",
                listOf(
                    SleepSessionRecord.Stage(t(0), t(30), SleepSessionRecord.STAGE_TYPE_LIGHT),
                    SleepSessionRecord.Stage(t(30), t(120), SleepSessionRecord.STAGE_TYPE_DEEP),
                    SleepSessionRecord.Stage(t(120), t(150), SleepSessionRecord.STAGE_TYPE_REM),
                    SleepSessionRecord.Stage(t(150), t(160), SleepSessionRecord.STAGE_TYPE_DEEP),
                ),
            ),
        )

        val sessions = HealthConnectManager.fromOtherApps(records, ownPackage = "com.sleeppulse.app", originOf = originOf)

        val only = sessions.single()
        assertEquals("com.samsung.android.wear.shealth", only.sourcePackage)
        assertEquals(100, only.deepSleepMinutes)
        assertEquals(30, only.remSleepMinutes)
        assertEquals(start + 8 * 3_600_000L, only.endMillis)
    }

    @Test
    fun `a record without stages reports zero deep and rem rather than a guess`() {
        val only = HealthConnectManager.fromOtherApps(listOf(hcRecord("other.app")), "com.sleeppulse.app", originOf).single()
        assertEquals(0, only.deepSleepMinutes)
        assertEquals(0, only.remSleepMinutes)
    }

    private fun hr(offsetMs: Long, bpm: Int) = SensorReading(start + offsetMs, bpm, 50.0, SleepStage.LIGHT)

    @Test
    fun `heart rate is one sample per minute, the mean of that minute`() {
        // Minute 0: 60, 62, 64 -> 62. Minute 1: 70, 71 -> 70.5 rounds to 71. Minute 2: 55.
        val readings = listOf(
            hr(0, 60), hr(20_000, 62), hr(40_000, 64),
            hr(60_000, 70), hr(90_000, 71),
            hr(120_000, 55),
        )

        val record = HealthConnectManager.buildHeartRateRecord(readings, zone)!!

        assertEquals(listOf(62L, 71L, 55L), record.samples.map { it.beatsPerMinute })
        assertEquals(Instant.ofEpochMilli(start), record.startTime)
        assertEquals(Instant.ofEpochMilli(start + 120_000), record.endTime)
        assertEquals(listOf(start, start + 60_000, start + 120_000), record.samples.map { it.time.toEpochMilli() })
        assertEquals("sleeppulse-hr-$start", record.metadata.clientRecordId)
        assertEquals(zone.rules.getOffset(Instant.ofEpochMilli(start)), record.startZoneOffset)
    }

    @Test
    fun `sensor dropouts outside 1 to 300 bpm are dropped instead of failing the insert`() {
        val readings = listOf(hr(0, 0), hr(1_000, 58), hr(61_000, 400), hr(62_000, 60))

        val record = HealthConnectManager.buildHeartRateRecord(readings, zone)!!

        assertEquals(listOf(58L, 60L), record.samples.map { it.beatsPerMinute })
        assertEquals(Instant.ofEpochMilli(start + 1_000), record.startTime)
    }

    @Test
    fun `too few valid readings or no duration gives no heart-rate record`() {
        assertNull(HealthConnectManager.buildHeartRateRecord(listOf(hr(0, 60)), zone))
        assertNull(HealthConnectManager.buildHeartRateRecord(listOf(hr(0, 60), hr(0, 61)), zone))
        assertNull(HealthConnectManager.buildHeartRateRecord(listOf(hr(0, 0), hr(1_000, 0)), zone))
    }

    @Test
    fun `required permissions cover both the sleep and heart-rate writes`() {
        assertEquals(2, HealthConnectManager.REQUIRED_PERMISSIONS.size)
        assertEquals(true, HealthConnectManager.REQUIRED_PERMISSIONS.any { it.endsWith("WRITE_HEART_RATE") })
    }
}
