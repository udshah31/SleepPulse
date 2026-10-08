package com.sleeppulse.shared.repository

import androidx.room.Transactor.SQLiteTransactionType
import androidx.room.useWriterConnection
import com.sleeppulse.shared.db.SleepPulseDatabase
import com.sleeppulse.shared.db.SleepSessionEntity
import com.sleeppulse.shared.model.*
import com.sleeppulse.shared.sleep.NightSummaryBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Persistence boundary allows lifecycle tests to control slow and failed writes. */
interface SessionStorage {
    fun recentNights(): Flow<List<NightlySummary>>
    suspend fun create(startMillis: Long): SleepSessionEntity
    suspend fun append(sessionId: Long, reading: SensorReading)
    suspend fun unfinished(): List<SleepSessionEntity>
    suspend fun finish(session: SleepSessionEntity): NightlySummary?
    suspend fun record(summary: NightlySummary)
    suspend fun updateTags(date: LocalDate, tags: List<String>)
}

class RoomSessionStorage(private val database: SleepPulseDatabase) : SessionStorage {
    private val sessions = database.sleepSessionDao()
    private val nights = database.nightlySummaryDao()
    override fun recentNights() = nights.observeRecent().map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(startMillis: Long): SleepSessionEntity {
        val row = SleepSessionEntity(startEpochMillis = startMillis, finalized = false)
        return row.copy(sessionId = sessions.createSession(row))
    }

    override suspend fun append(sessionId: Long, reading: SensorReading) =
        sessions.insertReadings(listOf(reading.toEntity(sessionId)))

    override suspend fun unfinished() = sessions.unfinalizedSessions()

    override suspend fun finish(session: SleepSessionEntity): NightlySummary? =
        database.useWriterConnection { connection ->
            connection.withTransaction(SQLiteTransactionType.IMMEDIATE) {
                // Re-check within the same transaction so recovery is idempotent even across callers.
                if (sessions.unfinalizedSessions().none { it.sessionId == session.sessionId }) return@withTransaction null
                val readings = sessions.readingsFor(session.sessionId).map { it.toReading() }
                val summary = readings.takeIf { it.isNotEmpty() }
                    ?.let { NightSummaryBuilder.build(it, localDateAt(session.startEpochMillis)) }
                if (summary != null) merge(summary)
                sessions.finalizeAndClear(session.sessionId)
                summary
            }
        }

    override suspend fun record(summary: NightlySummary) {
        database.useWriterConnection { connection ->
            connection.withTransaction(SQLiteTransactionType.IMMEDIATE) { merge(summary) }
        }
    }

    private suspend fun merge(summary: NightlySummary) {
        val existing = nights.getByDate(summary.date.toEpochDays().toLong())?.toDomain()
        nights.upsert(nightToKeep(existing, summary).toEntity())
        nights.trimToLast30Days()
    }

    override suspend fun updateTags(date: LocalDate, tags: List<String>) =
        nights.updateTags(date.toEpochDays().toLong(), tags)
}
