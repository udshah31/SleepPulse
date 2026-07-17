package com.sleeppulse.app.data.model

import java.time.LocalDate

/** Domain-level view of one night, decoupled from the Room entity shape. */
data class NightlySummary(
    val date: LocalDate,
    val sleepScore: Int,
    val avgHeartRateBpm: Int,
    val avgHrvMillis: Double,
    val totalSleepMinutes: Int,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
) {
    enum class Trend { UP, DOWN, FLAT }

    fun trendAgainst(previous: NightlySummary?): Trend = when {
        previous == null -> Trend.FLAT
        sleepScore > previous.sleepScore + 2 -> Trend.UP
        sleepScore < previous.sleepScore - 2 -> Trend.DOWN
        else -> Trend.FLAT
    }
}
