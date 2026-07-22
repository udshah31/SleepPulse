package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary
import kotlin.math.sqrt

enum class VariabilityLevel { LOW, MODERATE, HIGH }

data class SleepVariabilityResult(
    val hrvStdDev: Double,
    val hrStdDev: Double,
    val level: VariabilityLevel,
)

/**
 * Measures night-to-night consistency (not average) of HRV/HR over the most recent
 * [WINDOW_NIGHTS] nights. Someone whose recovery signals swing wildly night to night tends
 * to have less stable recovery than someone with a steady, even average — a distinct
 * insight from either [RecoveryScoreCalculator] or [HrvTrendCalculator].
 *
 * Requires at least [WINDOW_NIGHTS] nights of history; returns null otherwise.
 */
object SleepVariabilityCalculator {

    private const val WINDOW_NIGHTS = 7
    private const val LOW_HRV_STDDEV = 5.0
    private const val HIGH_HRV_STDDEV = 12.0

    fun analyze(nights: List<NightlySummary>): SleepVariabilityResult? {
        if (nights.size < WINDOW_NIGHTS) return null

        val window = nights.take(WINDOW_NIGHTS)
        val hrvStdDev = stdDev(window.map { it.avgHrvMillis })
        val hrStdDev = stdDev(window.map { it.avgHeartRateBpm.toDouble() })

        val level = when {
            hrvStdDev >= HIGH_HRV_STDDEV -> VariabilityLevel.HIGH
            hrvStdDev >= LOW_HRV_STDDEV -> VariabilityLevel.MODERATE
            else -> VariabilityLevel.LOW
        }

        return SleepVariabilityResult(hrvStdDev = hrvStdDev, hrStdDev = hrStdDev, level = level)
    }

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}
