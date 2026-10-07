package com.sleeppulse.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SleepSessionDao {
    @Insert
    suspend fun createSession(session: SleepSessionEntity): Long

    @Insert
    suspend fun insertReadings(readings: List<SessionReadingEntity>)

    @Query("SELECT * FROM sleep_session WHERE finalized = 0")
    suspend fun unfinalizedSessions(): List<SleepSessionEntity>

    @Query("SELECT * FROM session_reading WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun readingsFor(sessionId: Long): List<SessionReadingEntity>

    @Query("UPDATE sleep_session SET finalized = 1 WHERE sessionId = :sessionId")
    suspend fun markFinalized(sessionId: Long)

    @Query("DELETE FROM session_reading WHERE sessionId = :sessionId")
    suspend fun deleteReadings(sessionId: Long)

    @Transaction
    suspend fun finalizeAndClear(sessionId: Long) {
        markFinalized(sessionId)
        deleteReadings(sessionId)
    }
}
