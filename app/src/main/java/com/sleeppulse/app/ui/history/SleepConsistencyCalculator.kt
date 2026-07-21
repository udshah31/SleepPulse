package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import kotlin.math.pow
import kotlin.math.sqrt

object SleepConsistencyCalculator {

    /**
     * Calculates a consistency score (0-100) based on the standard deviation of bedtimes
     * over the provided nights (typically the last 7 days).
     *
     * Returns null if there are fewer than 2 nights.
     */
    fun calculateScore(nights: List<NightlySummary>): Int? {
        if (nights.size < 2) return null

        val bedtimeMinutes = nights.map { summary ->
            val zonedDateTime = Instant.ofEpochMilli(summary.bedtimeEpochMillis)
                .atZone(ZoneId.systemDefault())
            
            val hour = zonedDateTime.get(ChronoField.HOUR_OF_DAY)
            val minute = zonedDateTime.get(ChronoField.MINUTE_OF_HOUR)
            
            // Normalize to "minutes past 6 PM" to handle midnight wrap-around safely.
            // 18:00 (6 PM) = 0 mins
            // 23:00 (11 PM) = 300 mins
            // 01:00 (1 AM) = 420 mins
            val normalizedHour = if (hour >= 18) hour - 18 else hour + 6
            (normalizedHour * 60) + minute
        }

        val mean = bedtimeMinutes.average()
        val variance = bedtimeMinutes.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Score mapping:
        // stdDev <= 30 mins -> 100
        // stdDev == 60 mins -> 80
        // stdDev >= 120 mins -> 50 (minimum)
        // We can interpolate linearly or use a simple formula.
        
        val score = when {
            stdDev <= 30.0 -> 100.0
            stdDev >= 120.0 -> 50.0
            else -> {
                // Map [30, 120] to [100, 50]
                val fraction = (stdDev - 30.0) / (120.0 - 30.0)
                100.0 - (fraction * 50.0)
            }
        }
        
        return score.toInt()
    }
}
