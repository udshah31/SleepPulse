package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary

enum class TrendDirection { RISING, FALLING, STABLE }

data class HrvTrendResult(
    val recentAvgHrv: Double,
    val priorAvgHrv: Double,
    val direction: TrendDirection,
)

/**
 * Compares the average HRV of the most recent [WINDOW_NIGHTS] nights against the
 * [WINDOW_NIGHTS] nights before that, to catch a slow multi-week decline that a single
 * night-vs-baseline comparison (see [RecoveryScoreCalculator]) can't see.
 *
 * Requires at least 2*[WINDOW_NIGHTS] nights of history; returns null otherwise.
 */
object HrvTrendCalculator {

    private const val WINDOW_NIGHTS = 7
    private const val STABLE_THRESHOLD = 0.05 // +/-5% counts as stable

    fun analyze(nights: List<NightlySummary>): HrvTrendResult? {
        if (nights.size < WINDOW_NIGHTS * 2) return null

        val recentWindow = nights.take(WINDOW_NIGHTS)
        val priorWindow = nights.drop(WINDOW_NIGHTS).take(WINDOW_NIGHTS)

        val recentHrv = recentWindow.mapNotNull { it.avgHrvMillis }
        val priorHrv = priorWindow.mapNotNull { it.avgHrvMillis }
        if (recentHrv.isEmpty() || priorHrv.isEmpty()) return null
        val recentAvg = recentHrv.average()
        val priorAvg = priorHrv.average()

        val change = (recentAvg - priorAvg) / priorAvg
        val direction = when {
            change <= -STABLE_THRESHOLD -> TrendDirection.FALLING
            change >= STABLE_THRESHOLD -> TrendDirection.RISING
            else -> TrendDirection.STABLE
        }

        return HrvTrendResult(recentAvgHrv = recentAvg, priorAvgHrv = priorAvg, direction = direction)
    }
}
