package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class SleepDebtCalculatorTest {

    private fun night(totalSleepMinutes: Int) = NightlySummary(
        date = LocalDate(2026, 7, 21),
        sleepScore = 70,
        avgHeartRateBpm = 60,
        avgHrvMillis = 50.0,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `empty list returns null`() {
        assertNull(SleepDebtCalculator.calculate(emptyList()))
    }

    @Test
    fun `exactly on target every night returns CAUGHT_UP with zero deficit`() {
        val nights = List(3) { night(480) }
        val result = SleepDebtCalculator.calculate(nights)!!

        assertEquals(0, result.deficitMinutes)
        assertEquals(DebtLevel.CAUGHT_UP, result.level)
        assertEquals(3, result.nightsInWindow)
    }

    @Test
    fun `surplus sleep is clamped to zero (doesn't cancel deficit)`() {
        // 9 hours every night — surplus, no debt
        val nights = List(3) { night(540) }
        val result = SleepDebtCalculator.calculate(nights)!!

        assertEquals(0, result.deficitMinutes)
        assertEquals(DebtLevel.CAUGHT_UP, result.level)
    }

    @Test
    fun `window is capped at 7 nights even when more are provided`() {
        // 8 nights all at 420 min (60 min short each). Only 7 should count: 7 * 60 = 420.
        val nights = List(8) { night(420) }
        val result = SleepDebtCalculator.calculate(nights)!!

        assertEquals(7 * 60, result.deficitMinutes)
        assertEquals(7, result.nightsInWindow)
    }

    @Test
    fun `31–90 min total deficit is MILD`() {
        // One night 60 min short, rest on target → total deficit = 60
        val nights = listOf(night(420)) + List(2) { night(480) }
        val result = SleepDebtCalculator.calculate(nights)!!

        assertEquals(60, result.deficitMinutes)
        assertEquals(DebtLevel.MILD, result.level)
    }

    @Test
    fun `91–180 min total deficit is MODERATE`() {
        // Three nights 40 min short each → 120 min total
        val nights = List(3) { night(440) }
        val result = SleepDebtCalculator.calculate(nights)!!

        assertEquals(120, result.deficitMinutes)
        assertEquals(DebtLevel.MODERATE, result.level)
    }

    @Test
    fun `over 180 min total deficit is SEVERE`() {
        // Seven nights 30 min short each → 210 min total
        val nights = List(7) { night(450) }
        val result = SleepDebtCalculator.calculate(nights)!!

        assertEquals(210, result.deficitMinutes)
        assertEquals(DebtLevel.SEVERE, result.level)
    }
}
