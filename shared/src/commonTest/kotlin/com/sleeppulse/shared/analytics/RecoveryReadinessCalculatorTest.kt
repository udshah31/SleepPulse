package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.scoring.HrvTrendResult
import com.sleeppulse.shared.scoring.RestingHeartRateTrendResult
import com.sleeppulse.shared.scoring.TrendDirection
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RecoveryReadinessCalculatorTest {

    private fun hrvTrend(direction: TrendDirection) = HrvTrendResult(50.0, 50.0, direction)
    private fun rhrTrend(direction: TrendDirection) = RestingHeartRateTrendResult(55.0, 55.0, direction)
    private fun debt(minutes: Int) = SleepDebt(deficitMinutes = minutes, nightsInWindow = 7, level = DebtLevel.MILD)

    @Test
    fun `returns null when there is no recovery score`() {
        assertNull(RecoveryReadinessCalculator.compute(null, null, null, null))
    }

    @Test
    fun `pure recovery score passes through unchanged with no trend or debt data`() {
        val result = RecoveryReadinessCalculator.compute(75, null, null, null)
        assertEquals(75, result?.score)
        assertEquals(ReadinessTier.HIGH, result?.tier)
    }

    @Test
    fun `falling HRV trend and rising resting HR both penalize the composite score`() {
        val result = RecoveryReadinessCalculator.compute(
            recoveryScore = 75,
            hrvTrend = hrvTrend(TrendDirection.FALLING),
            rhrTrend = rhrTrend(TrendDirection.RISING),
            sleepDebt = null,
        )
        assertEquals(55, result?.score)
    }

    @Test
    fun `sleep debt subtracts points capped at 20`() {
        val result = RecoveryReadinessCalculator.compute(
            recoveryScore = 75,
            hrvTrend = null,
            rhrTrend = null,
            sleepDebt = debt(minutes = 900),
        )
        assertEquals(55, result?.score)
    }

    @Test
    fun `score is clamped into the 0 to 100 range`() {
        val result = RecoveryReadinessCalculator.compute(
            recoveryScore = 5,
            hrvTrend = hrvTrend(TrendDirection.FALLING),
            rhrTrend = rhrTrend(TrendDirection.RISING),
            sleepDebt = debt(minutes = 900),
        )
        assertEquals(0, result?.score)
        assertEquals(ReadinessTier.LOW, result?.tier)
    }

    @Test
    fun `tier boundaries match documented thresholds`() {
        assertEquals(ReadinessTier.HIGH, RecoveryReadinessCalculator.compute(70, null, null, null)?.tier)
        assertEquals(ReadinessTier.MODERATE, RecoveryReadinessCalculator.compute(69, null, null, null)?.tier)
        assertEquals(ReadinessTier.MODERATE, RecoveryReadinessCalculator.compute(40, null, null, null)?.tier)
        assertEquals(ReadinessTier.LOW, RecoveryReadinessCalculator.compute(39, null, null, null)?.tier)
    }
}
