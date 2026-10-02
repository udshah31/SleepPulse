package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HrvTrendCalculatorTest {

    private fun night(daysAgo: Int, hrv: Double?) = NightlySummary(
        date = LocalDate.now().minusDays(daysAgo.toLong()),
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
