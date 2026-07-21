package com.sleeppulse.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per night, cached locally so History can render trends offline. */
@Entity(tableName = "nightly_summary")
data class NightlySummaryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val bedtimeEpochMillis: Long = 0L,
    val sleepScore: Int,
    val avgHeartRateBpm: Int,
    val avgHrvMillis: Double,
    val totalSleepMinutes: Int,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
    val tags: List<String> = emptyList(),
)
