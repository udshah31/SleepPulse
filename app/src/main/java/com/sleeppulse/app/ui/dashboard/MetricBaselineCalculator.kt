package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary

private const val WINDOW_NIGHTS = 7
private const val MIN_NIGHTS_FOR_BASELINE = 3

/** 7-day rolling HR/HRV averages, used to show tonight's live reading vs. baseline. */
data class MetricBaselineResult(
    val avgHeartRateBpm: Double,
    val avgHrvMillis: Double,
)

/**
 * Computes rolling HR/HRV baselines from recorded nights, and a shared percent-delta
 * helper for comparing a live reading against those baselines.
 */
object MetricBaselineCalculator {

    fun compute(nights: List<NightlySummary>): MetricBaselineResult? {
        if (nights.size < MIN_NIGHTS_FOR_BASELINE) return null

        val window = nights.take(WINDOW_NIGHTS)
        return MetricBaselineResult(
            avgHeartRateBpm = window.map { it.avgHeartRateBpm }.average(),
            avgHrvMillis = window.map { it.avgHrvMillis }.average(),
        )
    }

    fun percentDelta(actual: Double, baseline: Double): Double {
        if (baseline == 0.0) return 0.0
        return (actual - baseline) / baseline * 100.0
    }
}
