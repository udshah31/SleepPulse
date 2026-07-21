package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.local.SessionReadingEntity
import com.sleeppulse.app.data.local.SleepSessionDao
import com.sleeppulse.app.data.local.SleepSessionEntity

class FakeSleepSessionDao : SleepSessionDao {
    val sessions: MutableList<SleepSessionEntity> = mutableListOf()
    val readings: MutableList<SessionReadingEntity> = mutableListOf()
    val recordedCalls: MutableList<String> = mutableListOf()

    /** Session ids for which [readingsFor] should throw, simulating a corrupt/unreadable session. */
    val readingsForFailures: MutableSet<Long> = mutableSetOf()

    private var nextSessionId = 1L
    private var nextReadingId = 1L

    override suspend fun createSession(session: SleepSessionEntity): Long {
        recordedCalls.add("createSession")
        val id = nextSessionId++
        sessions.add(session.copy(sessionId = id))
        return id
    }

    override suspend fun insertReadings(readings: List<SessionReadingEntity>) {
        recordedCalls.add("insertReadings:${readings.size}")
        readings.forEach { this.readings.add(it.copy(id = nextReadingId++)) }
    }

    override suspend fun unfinalizedSessions(): List<SleepSessionEntity> =
        sessions.filter { !it.finalized }

    override suspend fun readingsFor(sessionId: Long): List<SessionReadingEntity> {
        if (sessionId in readingsForFailures) {
            error("Simulated failure reading session $sessionId")
        }
        return readings.filter { it.sessionId == sessionId }.sortedBy { it.timestampMillis }
    }

    override suspend fun markFinalized(sessionId: Long) {
        recordedCalls.add("markFinalized")
        val index = sessions.indexOfFirst { it.sessionId == sessionId }
        if (index >= 0) sessions[index] = sessions[index].copy(finalized = true)
    }

    override suspend fun deleteReadings(sessionId: Long) {
        recordedCalls.add("deleteReadings")
        readings.removeAll { it.sessionId == sessionId }
    }

    override suspend fun finalizeAndClear(sessionId: Long) {
        markFinalized(sessionId)
        deleteReadings(sessionId)
    }
}
