package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class MetricBaselineCalculatorTest {

    private fun night(hrv: Double?, hr: Int) = NightlySummary(
        date = LocalDate(2026, 7, 18),
        sleepScore = 70,
        avgHeartRateBpm = hr,
        avgHrvMillis = hrv,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `fewer than 3 nights returns null`() {
        assertNull(MetricBaselineCalculator.compute(emptyList()))
        assertNull(MetricBaselineCalculator.compute(listOf(night(50.0, 60))))
        assertNull(MetricBaselineCalculator.compute(listOf(night(50.0, 60), night(50.0, 60))))
    }

    @Test
    fun `averages HR and HRV across up to 7 most recent nights`() {
        val nights = listOf(
            night(60.0, 50), night(50.0, 60), night(40.0, 70),
        )

        val result = MetricBaselineCalculator.compute(nights)

        assertNotNull(result)
        assertEquals(50.0, result!!.avgHrvMillis!!, 0.001)
        assertEquals(60.0, result.avgHeartRateBpm, 0.001)
    }

    @Test
    fun `only the first 7 nights (most recent) count toward the average`() {
        val recentSeven = List(7) { night(hrv = 60.0, hr = 50) }
        val olderNights = List(5) { night(hrv = 20.0, hr = 90) }

        val result = MetricBaselineCalculator.compute(recentSeven + olderNights)

        assertNotNull(result)
        assertEquals(60.0, result!!.avgHrvMillis!!, 0.001)
        assertEquals(50.0, result.avgHeartRateBpm, 0.001)
    }

    @Test
    fun `percentDelta computes signed percentage change from baseline`() {
        assertEquals(10.0, MetricBaselineCalculator.percentDelta(actual = 55.0, baseline = 50.0), 0.001)
        assertEquals(-10.0, MetricBaselineCalculator.percentDelta(actual = 45.0, baseline = 50.0), 0.001)
        assertEquals(0.0, MetricBaselineCalculator.percentDelta(actual = 50.0, baseline = 0.0), 0.001)
    }

    @Test
    fun `baseline hrv is null when no night has one`() {
        val result = MetricBaselineCalculator.compute(List(3) { night(hrv = null, hr = 60) })!!
        assertNull(result.avgHrvMillis)
    }
}
