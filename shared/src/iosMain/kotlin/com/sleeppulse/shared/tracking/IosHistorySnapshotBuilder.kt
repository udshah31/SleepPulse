package com.sleeppulse.shared.tracking

import com.sleeppulse.shared.analytics.NightInsightsCalculator
import com.sleeppulse.shared.analytics.SleepDebtCalculator
import com.sleeppulse.shared.model.NightlySummary

/** One immutable history emission. Also usable by explicit previews without a controller/database. */
object IosHistorySnapshotBuilder {
    fun build(nights: List<NightlySummary>): IosHistorySnapshot {
        val result = NightInsightsCalculator.calculate(nights)
        return IosHistorySnapshot(
            nights = nights.map { IosNightSnapshot(it.date.toEpochDays(), it.date.toString(), it.sleepScore,
                it.totalSleepMinutes, it.deepSleepMinutes, it.remSleepMinutes, it.avgHeartRateBpm, it.avgHrvMillis) },
            insights = IosNightInsightsSnapshot(
                recordedNights = result.recordedNights,
                baselineNights = result.baselineNights,
                latestIsoDate = result.latestDate?.toString(),
                recovery = result.recovery?.let { IosRecoverySnapshot(it.score, it.tier.name, it.guidance, it.hrvDeviation, it.rhrDeviation) },
                readiness = result.readiness?.let { IosReadinessSnapshot(it.score, it.tier.name) },
                debt = result.sleepDebt?.let { IosSleepDebtSnapshot(it.deficitMinutes, it.nightsInWindow, it.level.name, SleepDebtCalculator.DEFAULT_TARGET_MINUTES) },
                consistencyScore = result.consistencyScore,
                hrvTrend = result.hrvTrend?.let { IosMetricTrendSnapshot(it.recentAvgHrv, it.priorAvgHrv, it.direction.name) },
                heartRateTrend = result.heartRateTrend?.let { IosMetricTrendSnapshot(it.recentAvgBpm, it.priorAvgBpm, it.direction.name) },
                recentKnownHrvNights = result.recentKnownHrvNights,
                priorKnownHrvNights = result.priorKnownHrvNights,
                scoreChanges = result.scoreChanges.map { IosScoreChangeSnapshot(it.epochDay, it.previousScore, it.direction?.name) },
            ),
        )
    }
}
