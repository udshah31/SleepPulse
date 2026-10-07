package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class HrvTrendCalculatorTest {

    private fun night(daysAgo: Int, hrv: Double?) = NightlySummary(
        date = LocalDate(2026, 7, 18).minus(daysAgo, DateTimeUnit.DAY),
        sleepScore = 80,
        avgHeartRateBpm = 55,
        avgHrvMillis = hrv,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 60,
    )

    @Test
    fun `returns null with fewer than 14 nights`() {
        val nights = (0 until 13).map { night(it, 50.0) }
        assertNull(HrvTrendCalculator.analyze(nights))
    }

    @Test
    fun `detects falling HRV trend when recent week is lower`() {
        val recent = (0 until 7).map { night(it, 40.0) }
        val prior = (7 until 14).map { night(it, 50.0) }
        val result = HrvTrendCalculator.analyze(recent + prior)

        assertEquals(TrendDirection.FALLING, result?.direction)
        assertEquals(40.0, result?.recentAvgHrv)
        assertEquals(50.0, result?.priorAvgHrv)
    }

    @Test
    fun `detects rising HRV trend when recent week is higher`() {
        val recent = (0 until 7).map { night(it, 60.0) }
        val prior = (7 until 14).map { night(it, 50.0) }
        val result = HrvTrendCalculator.analyze(recent + prior)

        assertEquals(TrendDirection.RISING, result?.direction)
    }

    @Test
    fun `small changes within threshold are STABLE`() {
        val recent = (0 until 7).map { night(it, 51.0) }
        val prior = (7 until 14).map { night(it, 50.0) }
        val result = HrvTrendCalculator.analyze(recent + prior)

        assertEquals(TrendDirection.STABLE, result?.direction)
    }

    @Test
    fun `a window with no hrv at all gives no trend`() {
        val nights = (0 until 7).map { night(it, null) } + (7 until 14).map { night(it, 50.0) }
        assertNull(HrvTrendCalculator.analyze(nights))
    }
}
