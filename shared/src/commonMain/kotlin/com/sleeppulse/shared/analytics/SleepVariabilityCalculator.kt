package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary
import kotlin.math.sqrt

enum class VariabilityLevel { LOW, MODERATE, HIGH }

data class SleepVariabilityResult(
    val hrvStdDev: Double,
    val hrStdDev: Double,
    val level: VariabilityLevel,
)

object SleepVariabilityCalculator {
    private const val WINDOW_NIGHTS = 7
    private const val LOW_HRV_STDDEV = 5.0
    private const val HIGH_HRV_STDDEV = 12.0

    fun analyze(nights: List<NightlySummary>): SleepVariabilityResult? {
        if (nights.size < WINDOW_NIGHTS) return null
        val window = nights.take(WINDOW_NIGHTS)
        val hrv = window.mapNotNull { it.avgHrvMillis }
        if (hrv.size < WINDOW_NIGHTS) return null
        val hrvStdDev = stdDev(hrv)
        val hrStdDev = stdDev(window.map { it.avgHeartRateBpm.toDouble() })
        val level = when {
            hrvStdDev >= HIGH_HRV_STDDEV -> VariabilityLevel.HIGH
            hrvStdDev >= LOW_HRV_STDDEV -> VariabilityLevel.MODERATE
            else -> VariabilityLevel.LOW
        }
        return SleepVariabilityResult(hrvStdDev, hrStdDev, level)
    }

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
