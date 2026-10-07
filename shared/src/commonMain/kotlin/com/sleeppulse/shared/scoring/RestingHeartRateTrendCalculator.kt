package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary

data class RestingHeartRateTrendResult(
    val recentAvgBpm: Double,
    val priorAvgBpm: Double,
    val direction: TrendDirection,
)

object RestingHeartRateTrendCalculator {
    private const val WINDOW_NIGHTS = 7
    private const val STABLE_THRESHOLD_BPM = 2.0

    fun analyze(nights: List<NightlySummary>): RestingHeartRateTrendResult? {
        if (nights.size < WINDOW_NIGHTS * 2) return null
        val recentAvg = nights.take(WINDOW_NIGHTS).map { it.avgHeartRateBpm }.average()
        val priorAvg = nights.drop(WINDOW_NIGHTS).take(WINDOW_NIGHTS).map { it.avgHeartRateBpm }.average()
        val change = recentAvg - priorAvg
        val direction = when {
            change >= STABLE_THRESHOLD_BPM -> TrendDirection.RISING
            change <= -STABLE_THRESHOLD_BPM -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }
        return RestingHeartRateTrendResult(recentAvg, priorAvg, direction)
    }
}
