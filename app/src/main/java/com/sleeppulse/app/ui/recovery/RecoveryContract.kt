package com.sleeppulse.app.ui.recovery

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.scoring.HrvTrendResult
import com.sleeppulse.shared.scoring.RecoveryResult
import com.sleeppulse.shared.scoring.RestingHeartRateTrendResult
import com.sleeppulse.shared.analytics.SleepDebt
import com.sleeppulse.shared.analytics.SleepVariabilityResult
import com.sleeppulse.shared.analytics.TagCorrelation
import com.sleeppulse.shared.analytics.ReadinessResult

data class RecoveryState(
    val isLoading: Boolean = true,
    val recoveryResult: RecoveryResult? = null,
    val sleepDebt: SleepDebt? = null,
    val consistencyScore: Int = 0,
    val latestNight: NightlySummary? = null,
    val personalizedAdvice: String? = null,
    val recordedNightsCount: Int = 0,
    val hrvTrend: HrvTrendResult? = null,
    val restingHeartRateTrend: RestingHeartRateTrendResult? = null,
    val variability: SleepVariabilityResult? = null,
    val tagCorrelations: List<TagCorrelation> = emptyList(),
    val readiness: ReadinessResult? = null,
)

sealed interface RecoveryIntent {
    data object Load : RecoveryIntent
}
