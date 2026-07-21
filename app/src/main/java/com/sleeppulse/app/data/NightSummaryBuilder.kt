package com.sleeppulse.app.data

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.ui.dashboard.SleepScoreCalculator
import java.time.LocalDate

/**
 * Converts a full session's [SensorReading]s into a storable [NightlySummary]. Caller must
 * ensure [readings] is non-empty — behavior is undefined otherwise.
 */
object NightSummaryBuilder {

    fun build(readings: List<SensorReading>, date: LocalDate): NightlySummary {
        val avgHeartRateBpm = readings.map { it.heartRateBpm }.average().toInt()
        val avgHrvMillis = readings.map { it.hrvMillis }.average()

        var totalMinutes = 0L
        var deepMinutes = 0L
        var remMinutes = 0L
        for (i in 0 until readings.size - 1) {
            val deltaMinutes = (readings[i + 1].timestampMillis - readings[i].timestampMillis) / 60_000L
            totalMinutes += deltaMinutes
            when (readings[i].sleepStage) {
                SleepStage.DEEP -> deepMinutes += deltaMinutes
                SleepStage.REM -> remMinutes += deltaMinutes
                SleepStage.AWAKE, SleepStage.LIGHT -> Unit
            }
        }

        val score = SleepScoreCalculator.score(readings)
        
        val tags = mutableListOf<String>()
        if (score < 65) {
            tags.add("Alcohol")
            tags.add("Late screen time")
        } else if (score > 85) {
            tags.add("Exercise")
            tags.add("Read a book")
        } else {
            tags.add("Caffeine")
        }

        return NightlySummary(
            date = date,
            bedtimeEpochMillis = readings.first().timestampMillis,
            sleepScore = score,
            avgHeartRateBpm = avgHeartRateBpm,
            avgHrvMillis = avgHrvMillis,
            totalSleepMinutes = totalMinutes.toInt(),
            deepSleepMinutes = deepMinutes.toInt(),
            remSleepMinutes = remMinutes.toInt(),
            tags = tags,
        )
    }
}
