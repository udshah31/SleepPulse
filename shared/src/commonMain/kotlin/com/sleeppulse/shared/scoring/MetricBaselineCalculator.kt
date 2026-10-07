package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary

data class MetricBaselineResult(
    val avgHeartRateBpm: Double,
    val avgHrvMillis: Double?,
)

object MetricBaselineCalculator {
    private const val WINDOW_NIGHTS = 7
    private const val MIN_NIGHTS_FOR_BASELINE = 3

    fun compute(nights: List<NightlySummary>): MetricBaselineResult? {
        if (nights.size < MIN_NIGHTS_FOR_BASELINE) return null
        val window = nights.take(WINDOW_NIGHTS)
        return MetricBaselineResult(
            avgHeartRateBpm = window.map { it.avgHeartRateBpm }.average(),
            avgHrvMillis = window.mapNotNull { it.avgHrvMillis }.average().takeUnless { it.isNaN() },
        )
    }

    fun percentDelta(actual: Double, baseline: Double): Double =
        if (baseline == 0.0) 0.0 else (actual - baseline) / baseline * 100.0
}
