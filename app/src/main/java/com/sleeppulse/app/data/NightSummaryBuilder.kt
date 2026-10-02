package com.sleeppulse.app.data

import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.data.model.StageSegment
import com.sleeppulse.app.ui.dashboard.SleepScoreCalculator
import java.time.LocalDate

/**
 * Converts a full session's [SensorReading]s into a storable [NightlySummary]. Caller must
 * ensure [readings] is non-empty — behavior is undefined otherwise.
 */
object NightSummaryBuilder {

    fun build(readings: List<SensorReading>, date: LocalDate): NightlySummary {
        val avgHeartRateBpm = readings.map { it.heartRateBpm }.average().toInt()
        val avgHrvMillis = readings.mapNotNull { it.hrvMillis }.average().takeUnless { it.isNaN() }

        // Accumulate milliseconds and convert once: readings arrive about once a second, so
        // truncating each gap to whole minutes would round every gap (and the night) to zero.
        var totalMs = 0L
        var deepMs = 0L
        var remMs = 0L
        for (i in 0 until readings.size - 1) {
            val deltaMs = readings[i + 1].timestampMillis - readings[i].timestampMillis
            totalMs += deltaMs
            when (readings[i].sleepStage) {
                SleepStage.DEEP -> deepMs += deltaMs
                SleepStage.REM -> remMs += deltaMs
                SleepStage.AWAKE, SleepStage.LIGHT -> Unit
            }
        }

        val score = SleepScoreCalculator.score(readings)

        return NightlySummary(
            date = date,
            bedtimeEpochMillis = readings.first().timestampMillis,
            sleepScore = score,
            avgHeartRateBpm = avgHeartRateBpm,
            avgHrvMillis = avgHrvMillis,
            totalSleepMinutes = (totalMs / 60_000L).toInt(),
            deepSleepMinutes = (deepMs / 60_000L).toInt(),
            remSleepMinutes = (remMs / 60_000L).toInt(),
        )
    }

    /** Merges consecutive same-stage readings into segments; each runs to the next reading. */
    fun segments(readings: List<SensorReading>): List<StageSegment> {
        val out = mutableListOf<StageSegment>()
        for (i in 0 until readings.size - 1) {
            val start = readings[i].timestampMillis
            val end = readings[i + 1].timestampMillis
            if (end <= start) continue
            val stage = readings[i].sleepStage
            val last = out.lastOrNull()
            if (last != null && last.stage == stage) {
                out[out.lastIndex] = last.copy(endMillis = end)
            } else {
                out.add(StageSegment(start, end, stage))
            }
        }
        return out
    }
}
