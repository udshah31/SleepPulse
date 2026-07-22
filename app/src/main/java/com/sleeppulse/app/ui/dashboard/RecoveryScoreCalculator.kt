package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import kotlin.math.abs

enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }

data class RecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val guidance: String,
    val hrvDeviation: Double,
    val rhrDeviation: Double,
)

/**
 * Compares last night's HR/HRV against a rolling baseline to estimate how recovered the
 * user is. Returns null when fewer than [MIN_BASELINE_NIGHTS] baseline nights are available —
 * showing a score off too little history would be misleading.
 */
object RecoveryScoreCalculator {

    private const val MIN_BASELINE_NIGHTS = 3
    private const val NEUTRAL_DEVIATION_THRESHOLD = 0.03 // +/-3% counts as "nothing notably moved"

    fun score(lastNight: NightlySummary, baseline: List<NightlySummary>): RecoveryResult? {
        if (baseline.size < MIN_BASELINE_NIGHTS) return null

        val baselineAvgHrv = baseline.map { it.avgHrvMillis }.average()
        val baselineAvgHr = baseline.map { it.avgHeartRateBpm }.average()

        val hrvDeviation = (lastNight.avgHrvMillis - baselineAvgHrv) / baselineAvgHrv
        val rhrDeviation = (baselineAvgHr - lastNight.avgHeartRateBpm) / baselineAvgHr

        val hrvComponent = (50 + hrvDeviation * 200).coerceIn(0.0, 100.0)
        val rhrComponent = (50 + rhrDeviation * 200).coerceIn(0.0, 100.0)

        val score = (hrvComponent * 0.6 + rhrComponent * 0.4).toInt().coerceIn(0, 100)
        val tier = tierFor(score)
        return RecoveryResult(
            score = score,
            tier = tier,
            guidance = guidanceFor(tier, hrvDeviation, rhrDeviation),
            hrvDeviation = hrvDeviation,
            rhrDeviation = rhrDeviation,
        )
    }

    private fun tierFor(score: Int): RecoveryTier = when {
        score >= 80 -> RecoveryTier.OPTIMAL
        score >= 60 -> RecoveryTier.ADEQUATE
        score >= 40 -> RecoveryTier.LOW
        else -> RecoveryTier.POOR
    }

    /**
     * Names whichever of HRV/resting-HR deviated furthest from baseline (by absolute
     * magnitude) in the guidance sentence, so the user learns *why* their score moved —
     * not just the tier. Falls back to the static per-tier message when neither deviation
     * clears [NEUTRAL_DEVIATION_THRESHOLD], so we don't manufacture a "why" when nothing
     * actually moved.
     *
     * Note: rhrDeviation is defined as (baselineAvgHr - lastNight) / baselineAvgHr, so a
     * *positive* rhrDeviation means resting HR is LOWER than baseline (favorable) — the
     * sign is flipped relative to hrvDeviation's "positive is favorable" convention above
     * it, matching how these two are already combined in [score].
     */
    private fun guidanceFor(tier: RecoveryTier, hrvDeviation: Double, rhrDeviation: Double): String {
        val hrvMagnitude = abs(hrvDeviation)
        val rhrMagnitude = abs(rhrDeviation)

        if (hrvMagnitude < NEUTRAL_DEVIATION_THRESHOLD && rhrMagnitude < NEUTRAL_DEVIATION_THRESHOLD) {
            return staticGuidanceFor(tier)
        }

        val percentText = { deviation: Double -> "${(abs(deviation) * 100).toInt()}%" }

        return if (hrvMagnitude >= rhrMagnitude) {
            if (hrvDeviation >= 0) {
                "Your HRV is ${percentText(hrvDeviation)} above your weekly average, suggesting strong recovery."
            } else {
                "Your HRV is ${percentText(hrvDeviation)} below your weekly average — consider an easier day."
            }
        } else {
            // rhrDeviation >= 0 means resting HR is LOWER than baseline (favorable).
            if (rhrDeviation >= 0) {
                "Your resting heart rate is ${percentText(rhrDeviation)} below your weekly average, suggesting strong recovery."
            } else {
                "Your resting heart rate is ${percentText(rhrDeviation)} above your weekly average — consider an easier day."
            }
        }
    }

    private fun staticGuidanceFor(tier: RecoveryTier): String = when (tier) {
        RecoveryTier.OPTIMAL -> "Fully recovered — good day to push yourself."
        RecoveryTier.ADEQUATE -> "Recovered — normal training/activity load is fine."
        RecoveryTier.LOW -> "Under-recovered — consider an easier day."
        RecoveryTier.POOR -> "Poorly recovered — prioritize rest today."
    }
}
