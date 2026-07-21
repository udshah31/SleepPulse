package com.sleeppulse.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tracks one connect-to-disconnect session so an interrupted night can be recovered on next launch. */
@Entity(tableName = "sleep_session")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val startEpochMillis: Long,
    val finalized: Boolean,
)
