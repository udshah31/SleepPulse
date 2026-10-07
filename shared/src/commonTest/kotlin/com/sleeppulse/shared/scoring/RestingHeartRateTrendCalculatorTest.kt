package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class RestingHeartRateTrendCalculatorTest {

    private fun night(daysAgo: Int, hr: Int) = NightlySummary(
        date = LocalDate(2026, 7, 18).minus(daysAgo, DateTimeUnit.DAY),
        sleepScore = 80,
        avgHeartRateBpm = hr,
        avgHrvMillis = 50.0,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 60,
    )

    @Test
    fun `returns null with fewer than 14 nights`() {
        val nights = (0 until 13).map { night(it, 55) }
        assertNull(RestingHeartRateTrendCalculator.analyze(nights))
    }

    @Test
    fun `rising resting HR over recent week is flagged RISING`() {
        val recent = (0 until 7).map { night(it, 62) }
        val prior = (7 until 14).map { night(it, 55) }
        val result = RestingHeartRateTrendCalculator.analyze(recent + prior)

        assertEquals(TrendDirection.RISING, result?.direction)
    }

    @Test
    fun `falling resting HR over recent week is flagged FALLING`() {
        val recent = (0 until 7).map { night(it, 50) }
        val prior = (7 until 14).map { night(it, 58) }
        val result = RestingHeartRateTrendCalculator.analyze(recent + prior)

        assertEquals(TrendDirection.FALLING, result?.direction)
    }

    @Test
    fun `changes under 2 bpm are STABLE`() {
        val recent = (0 until 7).map { night(it, 56) }
        val prior = (7 until 14).map { night(it, 55) }
        val result = RestingHeartRateTrendCalculator.analyze(recent + prior)

        assertEquals(TrendDirection.STABLE, result?.direction)
    }
}
