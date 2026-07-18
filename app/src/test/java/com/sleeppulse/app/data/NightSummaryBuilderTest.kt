package com.sleeppulse.app.data

import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.ui.dashboard.SleepScoreCalculator
import org.junit.Assert.assertEquals
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
        assertEquals(57.5, summary.avgHrvMillis, 0.0001)
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
}
