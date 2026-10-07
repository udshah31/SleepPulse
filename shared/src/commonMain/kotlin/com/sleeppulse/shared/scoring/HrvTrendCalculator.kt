package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary

enum class TrendDirection { RISING, FALLING, STABLE }

data class HrvTrendResult(
    val recentAvgHrv: Double,
    val priorAvgHrv: Double,
    val direction: TrendDirection,
)

object HrvTrendCalculator {
    private const val WINDOW_NIGHTS = 7
    private const val STABLE_THRESHOLD = 0.05

    fun analyze(nights: List<NightlySummary>): HrvTrendResult? {
        if (nights.size < WINDOW_NIGHTS * 2) return null
        val recentHrv = nights.take(WINDOW_NIGHTS).mapNotNull { it.avgHrvMillis }
        val priorHrv = nights.drop(WINDOW_NIGHTS).take(WINDOW_NIGHTS).mapNotNull { it.avgHrvMillis }
        if (recentHrv.isEmpty() || priorHrv.isEmpty()) return null
        val recentAvg = recentHrv.average()
        val priorAvg = priorHrv.average()
        val change = (recentAvg - priorAvg) / priorAvg
        val direction = when {
            change <= -STABLE_THRESHOLD -> TrendDirection.FALLING
            change >= STABLE_THRESHOLD -> TrendDirection.RISING
            else -> TrendDirection.STABLE
        }
        return HrvTrendResult(recentAvg, priorAvg, direction)
    }
}
