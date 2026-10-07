package com.sleeppulse.shared.sleep

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.model.SensorReading
import com.sleeppulse.shared.model.SleepStage
import com.sleeppulse.shared.model.StageSegment
import com.sleeppulse.shared.scoring.SleepScoreCalculator
import kotlinx.datetime.LocalDate

/** Converts a complete session into a storable nightly summary and stage segments. */
object NightSummaryBuilder {

    fun build(readings: List<SensorReading>, date: LocalDate): NightlySummary {
        val avgHeartRateBpm = readings.map { it.heartRateBpm }.average().toInt()
        val avgHrvMillis = readings.mapNotNull { it.hrvMillis }.average().takeUnless { it.isNaN() }

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

        return NightlySummary(
            date = date,
            bedtimeEpochMillis = readings.first().timestampMillis,
            sleepScore = SleepScoreCalculator.score(readings),
            avgHeartRateBpm = avgHeartRateBpm,
            avgHrvMillis = avgHrvMillis,
            totalSleepMinutes = (totalMs / 60_000L).toInt(),
            deepSleepMinutes = (deepMs / 60_000L).toInt(),
            remSleepMinutes = (remMs / 60_000L).toInt(),
        )
    }

    /** Merges consecutive same-stage readings; every segment runs to the next reading. */
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
