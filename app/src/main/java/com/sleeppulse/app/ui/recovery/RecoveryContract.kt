package com.sleeppulse.app.ui.recovery

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.ui.dashboard.HrvTrendResult
import com.sleeppulse.app.ui.dashboard.RecoveryResult
import com.sleeppulse.app.ui.dashboard.RestingHeartRateTrendResult
import com.sleeppulse.app.ui.history.SleepDebt
import com.sleeppulse.app.ui.history.SleepVariabilityResult
import com.sleeppulse.app.ui.history.TagCorrelation

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
