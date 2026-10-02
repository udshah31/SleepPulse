package com.sleeppulse.app.data

import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.data.model.StageSegment
import com.sleeppulse.app.ui.dashboard.SleepScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NightSummaryBuilderTest {

    private fun reading(timestampMillis: Long, stage: SleepStage) = SensorReading(
        timestampMillis = timestampMillis,
        heartRateBpm = 60,
        hrvMillis = 50.0,
        sleepStage = stage,
    )

    @Test
    fun `averages heart rate and hrv across all readings`() {
        val readings = listOf(
            reading(0L, SleepStage.AWAKE).copy(heartRateBpm = 58, hrvMillis = 60.0),
            reading(300_000L, SleepStage.LIGHT).copy(heartRateBpm = 62, hrvMillis = 55.0),
        )

        val date = LocalDate.of(2026, 7, 18)
        val summary = NightSummaryBuilder.build(readings, date)

        assertEquals(date, summary.date)
        assertEquals(60, summary.avgHeartRateBpm)
        assertEquals(57.5, summary.avgHrvMillis!!, 0.0001)
    }

    @Test
    fun `sums timestamp deltas per stage into total, deep, and rem minutes`() {
        // AWAKE 0->5min (5min AWAKE), LIGHT 5->15min (10min LIGHT), DEEP 15->45min (30min DEEP),
        // LIGHT 45->60min (15min LIGHT), REM 60->90min (30min REM). Total = 5+10+30+15+30 = 90.
        val readings = listOf(
            reading(0L, SleepStage.AWAKE),
            reading(300_000L, SleepStage.LIGHT),
            reading(900_000L, SleepStage.DEEP),
            reading(2_700_000L, SleepStage.LIGHT),
            reading(3_600_000L, SleepStage.REM),
            reading(5_400_000L, SleepStage.LIGHT),
        )

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(90, summary.totalSleepMinutes)
        assertEquals(30, summary.deepSleepMinutes)
        assertEquals(30, summary.remSleepMinutes)
    }

    @Test
    fun `sleepScore matches SleepScoreCalculator over the same readings`() {
        val readings = listOf(
            reading(0L, SleepStage.LIGHT),
            reading(300_000L, SleepStage.LIGHT),
        )

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(SleepScoreCalculator.score(readings), summary.sleepScore)
    }

    @Test
    fun `single reading produces zero total, deep, and rem minutes`() {
        val readings = listOf(reading(0L, SleepStage.DEEP))

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(0, summary.totalSleepMinutes)
        assertEquals(0, summary.deepSleepMinutes)
        assertEquals(0, summary.remSleepMinutes)
    }

    @Test
    fun `one-second cadence still yields real minutes, not zero`() {
        // 2 hours of readings once a second: LIGHT for the first hour, DEEP for the second.
        val readings = (0..7200).map { sec ->
            reading(sec * 1_000L, if (sec < 3600) SleepStage.LIGHT else SleepStage.DEEP)
        }

        val summary = NightSummaryBuilder.build(readings, LocalDate.of(2026, 7, 18))

        assertEquals(120, summary.totalSleepMinutes)
        assertEquals(60, summary.deepSleepMinutes)
    }

    @Test
    fun `segments merge consecutive same-stage readings and run to the next reading`() {
        val readings = listOf(
            reading(0L, SleepStage.LIGHT),
            reading(1_000L, SleepStage.LIGHT),
            reading(2_000L, SleepStage.DEEP),
            reading(3_000L, SleepStage.DEEP),
            reading(4_000L, SleepStage.LIGHT),
        )

        assertEquals(
            listOf(
                StageSegment(0L, 2_000L, SleepStage.LIGHT),
                StageSegment(2_000L, 4_000L, SleepStage.DEEP),
            ),
            NightSummaryBuilder.segments(readings),
        )
    }

    @Test
    fun `segments of fewer than two readings is empty`() {
        assertEquals(emptyList<StageSegment>(), NightSummaryBuilder.segments(listOf(reading(0L, SleepStage.LIGHT))))
    }

    @Test
    fun `builder never invents tags - tags are user-entered only`() {
        listOf(SleepStage.AWAKE, SleepStage.DEEP).forEach { stage ->
            val summary = NightSummaryBuilder.build(
                listOf(reading(0L, stage), reading(300_000L, stage)),
                LocalDate.of(2026, 7, 18),
            )
            assertEquals(emptyList<String>(), summary.tags)
        }
    }

    @Test
    fun `average hrv ignores unknown readings and is null when none are known`() {
        val date = LocalDate.of(2026, 1, 1)
        fun r(t: Long, hrv: Double?) = SensorReading(t, 60, hrv, SleepStage.LIGHT)
        assertEquals(40.0, NightSummaryBuilder.build(listOf(r(0, 40.0), r(1_000, null), r(2_000, 40.0)), date).avgHrvMillis!!, 0.0001)
        assertNull(NightSummaryBuilder.build(listOf(r(0, null), r(1_000, null)), date).avgHrvMillis)
    }
}
