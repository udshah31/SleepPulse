package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.scoring.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class NightInsightsCalculatorTest {
    private fun night(age: Int, hr: Int = 60, hrv: Double? = 50.0, minutes: Int = 480, score: Int = 70) =
        NightlySummary(date = LocalDate.fromEpochDays(20_000 - age),
            bedtimeEpochMillis = (20_000L - age) * 86_400_000L + 22 * 3_600_000L,
            sleepScore = score, avgHeartRateBpm = hr, avgHrvMillis = hrv,
            totalSleepMinutes = minutes, deepSleepMinutes = 90, remSleepMinutes = 100)

    @Test fun `empty history has no calculated metrics or fake date`() {
        val result = NightInsightsCalculator.calculate(emptyList())
        assertEquals(0, result.recordedNights)
        assertEquals(0, result.baselineNights)
        assertNull(result.latestDate)
        assertNull(result.recovery)
        assertNull(result.readiness)
        assertNull(result.sleepDebt)
        assertNull(result.consistencyScore)
        assertTrue(result.scoreChanges.isEmpty())
    }

    @Test fun `baseline stays unavailable until four distinct stored nights`() {
        for (count in 1..3) {
            val result = NightInsightsCalculator.calculate(List(count) { night(it) })
            assertEquals(count, result.recordedNights)
            assertEquals(count - 1, result.baselineNights)
            assertNull(result.recovery)
            assertNull(result.readiness)
            assertNull(result.hrvTrend)
            assertNull(result.heartRateTrend)
            assertEquals(if (count >= 2) 100 else null, result.consistencyScore)
        }
    }

    @Test fun `latest night is excluded from baseline and readiness preserves no debt result`() {
        val result = NightInsightsCalculator.calculate(listOf(night(0, 54, 62.5)) + List(3) { night(it + 1) })
        assertEquals(88, result.recovery?.score)
        assertEquals(RecoveryTier.OPTIMAL, result.recovery?.tier)
        assertEquals(0.25, result.recovery?.hrvDeviation)
        assertEquals(0.1, result.recovery!!.rhrDeviation, 0.00001)
        assertEquals(88, result.readiness?.score)
        assertEquals(ReadinessTier.HIGH, result.readiness?.tier)
        assertEquals(3, result.baselineNights)
        assertEquals(night(0).date, result.latestDate)
        assertEquals(0, result.sleepDebt?.deficitMinutes)
    }

    @Test fun `poison nights beyond seven baseline dates do not alter recovery`() {
        val nights = listOf(night(0, 54, 62.5)) + List(7) { night(it + 1) } +
            List(5) { night(it + 8, 150, 5.0) }
        val result = NightInsightsCalculator.calculate(nights)
        assertEquals(88, result.recovery?.score)
        assertEquals(7, result.baselineNights)
    }

    @Test fun `HR only recovery leaves HRV deviation unavailable`() {
        val nights = listOf(night(0, 54, null)) + List(3) { night(it + 1, hrv = null) }
        val result = NightInsightsCalculator.calculate(nights)
        assertEquals(70, result.recovery?.score)
        assertNull(result.recovery?.hrvDeviation)
        assertEquals(70, result.readiness?.score)
    }

    @Test fun `fourteen dates use two recorded night windows and adjust readiness`() {
        val nights = List(7) { night(it, 54, 60.0) } + List(7) { night(it + 7, 60, 50.0) }
        val result = NightInsightsCalculator.calculate(nights)
        assertEquals(TrendDirection.RISING, result.hrvTrend?.direction)
        assertEquals(60.0, result.hrvTrend?.recentAvgHrv)
        assertEquals(50.0, result.hrvTrend?.priorAvgHrv)
        assertEquals(TrendDirection.FALLING, result.heartRateTrend?.direction)
        assertEquals(54.0, result.heartRateTrend?.recentAvgBpm)
        assertEquals(60.0, result.heartRateTrend?.priorAvgBpm)
        assertEquals(64, result.readiness?.score)
        assertEquals(7, result.recentKnownHrvNights)
        assertEquals(7, result.priorKnownHrvNights)
    }

    @Test fun `known HRV coverage is counted without inventing missing values`() {
        val missingPrior = List(14) { night(it, hrv = if (it == 0) 60.0 else null) }
        val missing = NightInsightsCalculator.calculate(missingPrior)
        assertNull(missing.hrvTrend)
        assertNotNull(missing.heartRateTrend)
        assertEquals(1, missing.recentKnownHrvNights)
        assertEquals(0, missing.priorKnownHrvNights)
        val sparse = NightInsightsCalculator.calculate(missingPrior.mapIndexed { index, summary ->
            if (index == 7) summary.copy(avgHrvMillis = 50.0) else summary
        })
        assertEquals(TrendDirection.RISING, sparse.hrvTrend?.direction)
        assertEquals(1, sparse.priorKnownHrvNights)
    }

    @Test fun `debt uses last seven actual nights and preserves zero deficits`() {
        val nights = List(8) { night(it, minutes = when (it) { 0, 7 -> 0; 1 -> 460; else -> 480 }) }
        val result = NightInsightsCalculator.calculate(nights)
        assertEquals(500, result.sleepDebt?.deficitMinutes)
        assertEquals(7, result.sleepDebt?.nightsInWindow)
        assertEquals(DebtLevel.SEVERE, result.sleepDebt?.level)
        assertEquals(34, result.readiness?.score) // Neutral recovery 50 minus the 500-minute debt penalty 16.
        assertEquals(0, NightInsightsCalculator.calculate(listOf(night(0))).sleepDebt?.deficitMinutes)
    }

    @Test fun `consistency includes older retained bedtimes rather than a seven night slice`() {
        val nights = List(8) { night(it).let { summary ->
            if (it == 7) summary.copy(bedtimeEpochMillis = summary.bedtimeEpochMillis + 5 * 3_600_000L) else summary
        } }
        val result = NightInsightsCalculator.calculate(nights)
        assertTrue(result.consistencyScore!! < 100)
    }

    @Test fun `score comparisons preserve neutral range and omit the oldest comparison`() {
        val nights = listOf(night(0, score = 75), night(1, score = 72), night(2, score = 70), night(3, score = 73))
        val changes = NightInsightsCalculator.calculate(nights).scoreChanges
        assertEquals(listOf(NightlySummary.Trend.UP, NightlySummary.Trend.FLAT, NightlySummary.Trend.DOWN, null),
            changes.map { it.direction })
        assertEquals(listOf(72, 70, 73, null), changes.map { it.previousScore })
        assertEquals(nights.map { it.date.toEpochDays() }, changes.map { it.epochDay })
    }
}
