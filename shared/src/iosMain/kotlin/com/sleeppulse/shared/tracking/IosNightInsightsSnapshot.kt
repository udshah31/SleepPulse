package com.sleeppulse.shared.tracking

data class IosRecoverySnapshot(
    val score: Int, val tier: String, val guidance: String,
    val hrvDeviation: Double?, val heartRateDeviation: Double,
)
data class IosReadinessSnapshot(val score: Int, val tier: String)
data class IosMetricTrendSnapshot(val recentAverage: Double, val priorAverage: Double, val direction: String)
data class IosSleepDebtSnapshot(val deficitMinutes: Int, val nights: Int, val level: String, val targetMinutes: Int)
data class IosScoreChangeSnapshot(val epochDay: Int, val previousScore: Int?, val direction: String?)

data class IosNightInsightsSnapshot(
    val recordedNights: Int,
    val baselineNights: Int,
    val latestIsoDate: String?,
    val recovery: IosRecoverySnapshot?,
    val readiness: IosReadinessSnapshot?,
    val debt: IosSleepDebtSnapshot?,
    val consistencyScore: Int?,
    val hrvTrend: IosMetricTrendSnapshot?,
    val heartRateTrend: IosMetricTrendSnapshot?,
    val recentKnownHrvNights: Int,
    val priorKnownHrvNights: Int,
    val scoreChanges: List<IosScoreChangeSnapshot>,
)

data class IosHistorySnapshot(val nights: List<IosNightSnapshot>, val insights: IosNightInsightsSnapshot)
