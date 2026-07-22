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

    /**
     * Computes the 7-night rolling average. Deliberately includes the most recent
     * completed night in the window (unlike [RecoveryScoreCalculator], whose baseline
     * excludes the last night since it compares that night against the baseline) — here
     * the comparison target is a live in-progress reading, which is never itself in
     * [nights], so there's no night to hold out.
     */
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
