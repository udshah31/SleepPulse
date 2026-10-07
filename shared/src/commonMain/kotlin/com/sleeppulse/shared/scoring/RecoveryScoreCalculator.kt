package com.sleeppulse.shared.scoring

import com.sleeppulse.shared.model.NightlySummary
import kotlin.math.abs

enum class RecoveryTier { OPTIMAL, ADEQUATE, LOW, POOR }

data class RecoveryResult(
    val score: Int,
    val tier: RecoveryTier,
    val guidance: String,
    val hrvDeviation: Double?,
    val rhrDeviation: Double,
)

object RecoveryScoreCalculator {
    private const val MIN_BASELINE_NIGHTS = 3
    private const val NEUTRAL_DEVIATION_THRESHOLD = 0.03

    fun score(lastNight: NightlySummary, baseline: List<NightlySummary>): RecoveryResult? {
        if (baseline.size < MIN_BASELINE_NIGHTS) return null

        val baselineHrv = baseline.mapNotNull { it.avgHrvMillis }
        val baselineAvgHr = baseline.map { it.avgHeartRateBpm }.average()
        val lastHrv = lastNight.avgHrvMillis
        val hrvDeviation = if (lastHrv != null && baselineHrv.size >= MIN_BASELINE_NIGHTS) {
            val baselineAvgHrv = baselineHrv.average()
            (lastHrv - baselineAvgHrv) / baselineAvgHrv
        } else null
        val rhrDeviation = (baselineAvgHr - lastNight.avgHeartRateBpm) / baselineAvgHr
        val rhrComponent = (50 + rhrDeviation * 200).coerceIn(0.0, 100.0)
        val score = if (hrvDeviation != null) {
            val hrvComponent = (50 + hrvDeviation * 200).coerceIn(0.0, 100.0)
            (hrvComponent * 0.6 + rhrComponent * 0.4).toInt()
        } else rhrComponent.toInt()
        val clamped = score.coerceIn(0, 100)
        val tier = when {
            clamped >= 80 -> RecoveryTier.OPTIMAL
            clamped >= 60 -> RecoveryTier.ADEQUATE
            clamped >= 40 -> RecoveryTier.LOW
            else -> RecoveryTier.POOR
        }
        return RecoveryResult(
            score = clamped,
            tier = tier,
            guidance = guidanceFor(tier, hrvDeviation, rhrDeviation),
            hrvDeviation = hrvDeviation,
            rhrDeviation = rhrDeviation,
        )
    }

    fun scoreLatest(nights: List<NightlySummary>): RecoveryResult? {
        val lastNight = nights.firstOrNull() ?: return null
        return score(lastNight, nights.drop(1).take(7))
    }

    private fun guidanceFor(tier: RecoveryTier, hrvDeviation: Double?, rhrDeviation: Double): String {
        val hrvMagnitude = hrvDeviation?.let(::abs) ?: 0.0
        val rhrMagnitude = abs(rhrDeviation)
        if (hrvMagnitude < NEUTRAL_DEVIATION_THRESHOLD && rhrMagnitude < NEUTRAL_DEVIATION_THRESHOLD) {
            return when (tier) {
                RecoveryTier.OPTIMAL -> "Fully recovered — good day to push yourself."
                RecoveryTier.ADEQUATE -> "Recovered — normal training/activity load is fine."
                RecoveryTier.LOW -> "Under-recovered — consider an easier day."
                RecoveryTier.POOR -> "Poorly recovered — prioritize rest today."
            }
        }
        val percentText = { deviation: Double -> "${(abs(deviation) * 100).toInt()}%" }
        val recommendation = if (tier == RecoveryTier.OPTIMAL || tier == RecoveryTier.ADEQUATE) {
            "suggesting strong recovery."
        } else "consider an easier day."
        return if (hrvDeviation != null && hrvMagnitude >= rhrMagnitude) {
            val direction = if (hrvDeviation >= 0) "above" else "below"
            "Your HRV is ${percentText(hrvDeviation)} $direction your weekly average, $recommendation"
        } else {
            val direction = if (rhrDeviation >= 0) "below" else "above"
            "Your resting heart rate is ${percentText(rhrDeviation)} $direction your weekly average, $recommendation"
        }
    }
}
