package com.sleeppulse.app.ui.recovery

import com.sleeppulse.app.ui.dashboard.HrvTrendResult
import com.sleeppulse.app.ui.dashboard.RestingHeartRateTrendResult
import com.sleeppulse.app.ui.dashboard.TrendDirection
import com.sleeppulse.app.ui.history.SleepDebt

enum class ReadinessTier { HIGH, MODERATE, LOW }

data class ReadinessResult(
    val score: Int,
    val tier: ReadinessTier,
)

/**
 * Combines last night's recovery score, the multi-week HRV/resting-HR trends, and current
 * sleep debt into a single "how ready is your body today" figure — richer than any one
 * signal alone, since a single good (or bad) night can mask a multi-week trend in the
 * opposite direction.
 *
 * Trend/debt inputs are optional: with fewer than 14 nights of history, trends are simply
 * absent from the blend rather than the whole result being withheld.
 */
object RecoveryReadinessCalculator {

    private const val TREND_ADJUSTMENT = 10.0
    private const val TREND_BONUS = 5.0
    private const val DEBT_MINUTES_PER_POINT = 30
    private const val MAX_DEBT_PENALTY = 20

    fun compute(
        recoveryScore: Int?,
        hrvTrend: HrvTrendResult?,
        rhrTrend: RestingHeartRateTrendResult?,
        sleepDebt: SleepDebt?,
    ): ReadinessResult? {
        if (recoveryScore == null) return null

        var score = recoveryScore.toDouble()

        if (hrvTrend != null) {
            score += when (hrvTrend.direction) {
                TrendDirection.FALLING -> -TREND_ADJUSTMENT
                TrendDirection.RISING -> TREND_BONUS
                TrendDirection.STABLE -> 0.0
            }
        }

        if (rhrTrend != null) {
            score += when (rhrTrend.direction) {
                TrendDirection.RISING -> -TREND_ADJUSTMENT
                TrendDirection.FALLING -> TREND_BONUS
                TrendDirection.STABLE -> 0.0
            }
        }

        if (sleepDebt != null) {
            score -= (sleepDebt.deficitMinutes / DEBT_MINUTES_PER_POINT).coerceAtMost(MAX_DEBT_PENALTY)
        }

        val clamped = score.toInt().coerceIn(0, 100)
        val tier = when {
            clamped >= 70 -> ReadinessTier.HIGH
            clamped >= 40 -> ReadinessTier.MODERATE
            else -> ReadinessTier.LOW
        }

        return ReadinessResult(score = clamped, tier = tier)
    }
}
