package com.sleeppulse.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {
    @Test
    fun `trend thresholds remain unchanged`() {
        val base = summary(70)
        assertEquals(NightlySummary.Trend.UP, summary(73).trendAgainst(base))
        assertEquals(NightlySummary.Trend.DOWN, summary(67).trendAgainst(base))
        assertEquals(NightlySummary.Trend.FLAT, summary(72).trendAgainst(base))
    }

    @Test
    fun `sensor model keeps nullable hrv`() {
        val reading = SensorReading(1L, 60, null, SleepStage.LIGHT)
        assertEquals(null, reading.hrvMillis)
    }

    private fun summary(score: Int) = NightlySummary(
        date = LocalDate(2026, 7, 18),
        sleepScore = score,
        avgHeartRateBpm = 60,
        avgHrvMillis = 50.0,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )
}
