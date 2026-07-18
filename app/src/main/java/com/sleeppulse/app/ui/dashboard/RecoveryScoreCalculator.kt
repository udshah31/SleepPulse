package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary

enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }

data class RecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val guidance: String,
)

/**
 * Compares last night's HR/HRV against a rolling baseline to estimate how recovered the
 * user is. Returns null when fewer than [MIN_BASELINE_NIGHTS] baseline nights are available —
 * showing a score off too little history would be misleading.
 */
object RecoveryScoreCalculator {

    private const val MIN_BASELINE_NIGHTS = 3

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
        return RecoveryResult(score = score, tier = tier, guidance = guidanceFor(tier))
    }

    private fun tierFor(score: Int): RecoveryTier = when {
        score >= 80 -> RecoveryTier.OPTIMAL
        score >= 60 -> RecoveryTier.ADEQUATE
        score >= 40 -> RecoveryTier.LOW
        else -> RecoveryTier.POOR
    }

    private fun guidanceFor(tier: RecoveryTier): String = when (tier) {
        RecoveryTier.OPTIMAL -> "Fully recovered — good day to push yourself."
        RecoveryTier.ADEQUATE -> "Recovered — normal training/activity load is fine."
        RecoveryTier.LOW -> "Under-recovered — consider an easier day."
        RecoveryTier.POOR -> "Poorly recovered — prioritize rest today."
    }
}
