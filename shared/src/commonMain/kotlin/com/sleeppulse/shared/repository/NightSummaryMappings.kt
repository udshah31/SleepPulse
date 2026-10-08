package com.sleeppulse.shared.repository

import com.sleeppulse.shared.db.NightlySummaryEntity
import com.sleeppulse.shared.db.SessionReadingEntity
import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.model.SensorReading
import kotlinx.datetime.LocalDate

internal fun nightToKeep(existing: NightlySummary?, incoming: NightlySummary): NightlySummary {
    val winner = if (existing != null && existing.totalSleepMinutes > incoming.totalSleepMinutes) existing else incoming
    return winner.copy(tags = (existing?.tags.orEmpty() + incoming.tags).distinct())
}

internal fun NightlySummaryEntity.toDomain() = NightlySummary(
    LocalDate.fromEpochDays(dateEpochDay.toInt()), bedtimeEpochMillis, sleepScore,
    avgHeartRateBpm, avgHrvMillis, totalSleepMinutes, deepSleepMinutes, remSleepMinutes, tags,
)

internal fun NightlySummary.toEntity() = NightlySummaryEntity(
    date.toEpochDays().toLong(), bedtimeEpochMillis, sleepScore, avgHeartRateBpm, avgHrvMillis,
    totalSleepMinutes, deepSleepMinutes, remSleepMinutes, tags,
)

internal fun SessionReadingEntity.toReading() = SensorReading(timestampMillis, heartRateBpm, hrvMillis, sleepStage)

internal fun SensorReading.toEntity(sessionId: Long) = SessionReadingEntity(
    sessionId = sessionId, timestampMillis = timestampMillis, heartRateBpm = heartRateBpm,
    hrvMillis = hrvMillis, sleepStage = sleepStage,
)
