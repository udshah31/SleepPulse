package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SleepVariabilityCalculatorTest {

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
    fun `returns null with fewer than 7 nights`() {
        val nights = (0 until 6).map { night(it, 50.0) }
        assertNull(SleepVariabilityCalculator.analyze(nights))
    }

    @Test
    fun `identical nights report LOW variability with zero stddev`() {
        val nights = (0 until 7).map { night(it, 50.0) }
        val result = SleepVariabilityCalculator.analyze(nights)

        assertEquals(VariabilityLevel.LOW, result?.level)
        assertEquals(0.0, result?.hrvStdDev)
    }

    @Test
    fun `wildly swinging HRV reports HIGH variability`() {
        val hrvValues = listOf(20.0, 70.0, 25.0, 65.0, 22.0, 68.0, 24.0)
        val nights = hrvValues.mapIndexed { index, hrv -> night(index, hrv) }
        val result = SleepVariabilityCalculator.analyze(nights)

        assertEquals(VariabilityLevel.HIGH, result?.level)
    }

    @Test
    fun `only the most recent 7 nights are considered`() {
        val recentStable = (0 until 7).map { night(it, 50.0) }
        val oldErratic = (7 until 14).map { night(it, if (it % 2 == 0) 10.0 else 90.0) }
        val result = SleepVariabilityCalculator.analyze(recentStable + oldErratic)

        assertEquals(VariabilityLevel.LOW, result?.level)
    }

    @Test
    fun `fewer than seven nights with hrv gives no variability`() {
        val nights = (0 until 6).map { night(it, 50.0) } + night(6, null)
        assertNull(SleepVariabilityCalculator.analyze(nights))
    }
}
