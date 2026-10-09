package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.scoring.*
import kotlinx.datetime.LocalDate

data class NightScoreChange(val epochDay: Int, val previousScore: Int?, val direction: NightlySummary.Trend?)

data class NightInsights(
    val recordedNights: Int,
    val baselineNights: Int,
    val latestDate: LocalDate?,
    val recovery: RecoveryResult?,
    val readiness: ReadinessResult?,
    val sleepDebt: SleepDebt?,
    val consistencyScore: Int?,
    val hrvTrend: HrvTrendResult?,
    val heartRateTrend: RestingHeartRateTrendResult?,
    val recentKnownHrvNights: Int,
    val priorKnownHrvNights: Int,
    val scoreChanges: List<NightScoreChange>,
)

/** Composes the shared formulas over unique, newest-first persisted nights. No synthetic dates. */
object NightInsightsCalculator {
    fun calculate(nights: List<NightlySummary>): NightInsights {
        val recovery = RecoveryScoreCalculator.scoreLatest(nights)
        val debt = SleepDebtCalculator.calculate(nights)
        val hrv = HrvTrendCalculator.analyze(nights)
        val hr = RestingHeartRateTrendCalculator.analyze(nights)
        return NightInsights(
            recordedNights = nights.size,
            baselineNights = (nights.size - 1).coerceIn(0, 7),
            latestDate = nights.firstOrNull()?.date,
            recovery = recovery,
            readiness = RecoveryReadinessCalculator.compute(recovery?.score, hrv, hr, debt),
            sleepDebt = debt,
            consistencyScore = SleepConsistencyCalculator.calculateScore(nights),
            hrvTrend = hrv,
            heartRateTrend = hr,
            recentKnownHrvNights = nights.take(7).count { it.avgHrvMillis != null },
            priorKnownHrvNights = nights.drop(7).take(7).count { it.avgHrvMillis != null },
            scoreChanges = nights.mapIndexed { index, night ->
                val previous = nights.getOrNull(index + 1)
                NightScoreChange(night.date.toEpochDays(), previous?.sleepScore,
                    previous?.let { night.trendAgainst(it) })
            },
        )
    }
}
