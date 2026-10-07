package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.pow
import kotlin.math.sqrt

object SleepConsistencyCalculator {
    fun calculateScore(nights: List<NightlySummary>): Int? {
        if (nights.size < 2) return null
        val bedtimeMinutes = nights.map { summary ->
            val local = Instant.fromEpochMilliseconds(summary.bedtimeEpochMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val normalizedHour = if (local.hour >= 18) local.hour - 18 else local.hour + 6
            normalizedHour * 60 + local.minute
        }
        val mean = bedtimeMinutes.average()
        val stdDev = sqrt(bedtimeMinutes.map { (it - mean).pow(2) }.average())
        val score = when {
            stdDev <= 30.0 -> 100.0
            stdDev >= 120.0 -> 50.0
            else -> 100.0 - ((stdDev - 30.0) / 90.0 * 50.0)
        }
        return score.toInt()
    }
}
