package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary

data class RestingHeartRateTrendResult(
    val recentAvgBpm: Double,
    val priorAvgBpm: Double,
    val direction: TrendDirection,
)

/**
 * Compares average resting HR (proxied by [NightlySummary.avgHeartRateBpm], the only
 * per-night HR figure currently recorded) across two consecutive [WINDOW_NIGHTS]-night
 * windows. A sustained rise across weeks is one of the more reliable illness/overtraining
 * precursors, even when each individual night still looks unremarkable on its own.
 *
 * Requires at least 2*[WINDOW_NIGHTS] nights of history; returns null otherwise.
 */
object RestingHeartRateTrendCalculator {

    private const val WINDOW_NIGHTS = 7
    private const val STABLE_THRESHOLD_BPM = 2.0

    fun analyze(nights: List<NightlySummary>): RestingHeartRateTrendResult? {
        if (nights.size < WINDOW_NIGHTS * 2) return null

        val recentWindow = nights.take(WINDOW_NIGHTS)
        val priorWindow = nights.drop(WINDOW_NIGHTS).take(WINDOW_NIGHTS)

        val recentAvg = recentWindow.map { it.avgHeartRateBpm }.average()
        val priorAvg = priorWindow.map { it.avgHeartRateBpm }.average()

        val change = recentAvg - priorAvg
        val direction = when {
            change >= STABLE_THRESHOLD_BPM -> TrendDirection.RISING
            change <= -STABLE_THRESHOLD_BPM -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }

        return RestingHeartRateTrendResult(recentAvgBpm = recentAvg, priorAvgBpm = priorAvg, direction = direction)
    }
}
