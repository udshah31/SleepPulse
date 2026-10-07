package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.scoring.HrvTrendResult
import com.sleeppulse.shared.scoring.RestingHeartRateTrendResult
import com.sleeppulse.shared.scoring.TrendDirection

enum class ReadinessTier { HIGH, MODERATE, LOW }

data class ReadinessResult(val score: Int, val tier: ReadinessTier)

object RecoveryReadinessCalculator {
    fun compute(
        recoveryScore: Int?,
        hrvTrend: HrvTrendResult?,
        rhrTrend: RestingHeartRateTrendResult?,
        sleepDebt: SleepDebt?,
    ): ReadinessResult? {
        if (recoveryScore == null) return null
        var score = recoveryScore.toDouble()
        if (hrvTrend != null) score += when (hrvTrend.direction) {
            TrendDirection.FALLING -> -10.0
            TrendDirection.RISING -> 5.0
            TrendDirection.STABLE -> 0.0
        }
        if (rhrTrend != null) score += when (rhrTrend.direction) {
            TrendDirection.RISING -> -10.0
            TrendDirection.FALLING -> 5.0
            TrendDirection.STABLE -> 0.0
        }
        if (sleepDebt != null) score -= (sleepDebt.deficitMinutes / 30).coerceAtMost(20)
        val clamped = score.toInt().coerceIn(0, 100)
        val tier = when {
            clamped >= 70 -> ReadinessTier.HIGH
            clamped >= 40 -> ReadinessTier.MODERATE
            else -> ReadinessTier.LOW
        }
        return ReadinessResult(clamped, tier)
    }
}
