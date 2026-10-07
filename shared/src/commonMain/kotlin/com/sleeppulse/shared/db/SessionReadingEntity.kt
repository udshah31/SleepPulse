package com.sleeppulse.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sleeppulse.shared.model.SleepStage

/** One raw reading, batch-flushed to disk during a live session as a recovery safety net. */
@Entity(tableName = "session_reading")
data class SessionReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val hrvMillis: Double?,
    val sleepStage: SleepStage,
)
