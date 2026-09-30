package com.sleeppulse.app.tracking

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

class HealthConnectSleepSyncTest {

    private class MemoryStore(
        override var token: String? = null,
        override var sessions: List<ExternalSleepSession> = emptyList(),
    ) : SleepSyncStore

    private fun session(id: String, start: Long = 0L) =
        ExternalSleepSession(id, start, start + 1_000, 0, 0, "other.app")

    private val now = 1_780_000_000_000L

    @Test
    fun `first sync takes a token then does a full read of the window`() = runTest {
        val manager = mock<HealthConnectManager> {
            onBlocking { getChangesToken() } doReturn "t1"
            onBlocking { readSleepSessions(any(), any()) } doReturn listOf(session("a"))
        }
        val store = MemoryStore()
        val sync = HealthConnectSleepSync(manager, store) { now }

        sync.sync()

        assertEquals("t1", store.token)
        assertEquals(listOf(session("a")), sync.sessions.value)
        verifyBlocking(manager) {
            readSleepSessions(Instant.ofEpochMilli(now).minus(HealthConnectSleepSync.WINDOW), Instant.ofEpochMilli(now))
        }
    }

    @Test
    fun `later syncs apply only the changes and advance the token`() = runTest {
        val manager = mock<HealthConnectManager> {
            onBlocking { getSleepChanges("t1") } doReturn
                SleepChanges.Changes(upserted = listOf(session("b", 5)), deletedIds = listOf("a"), nextToken = "t2")
        }
        val store = MemoryStore(token = "t1", sessions = listOf(session("a"), session("c", 1)))
        val sync = HealthConnectSleepSync(manager, store) { now }

        sync.sync()

        assertEquals("t2", store.token)
        assertEquals(listOf(session("c", 1), session("b", 5)), store.sessions)
        verifyBlocking(manager, never()) { readSleepSessions(any(), any()) }
    }

    @Test
    fun `an expired token falls back to a full read with a fresh token`() = runTest {
        val manager = mock<HealthConnectManager> {
            onBlocking { getSleepChanges("old") } doReturn SleepChanges.TokenExpired
            onBlocking { getChangesToken() } doReturn "fresh"
            onBlocking { readSleepSessions(any(), any()) } doReturn listOf(session("x"))
        }
        val store = MemoryStore(token = "old", sessions = listOf(session("stale")))
        val sync = HealthConnectSleepSync(manager, store) { now }

        sync.sync()

        assertEquals("fresh", store.token)
        assertEquals(listOf(session("x")), store.sessions)
    }

    @Test
    fun `revoked read access drops the token and the cached data`() = runTest {
        val manager = mock<HealthConnectManager> {
            onBlocking { getSleepChanges("t1") } doReturn SleepChanges.NoPermission
        }
        val store = MemoryStore(token = "t1", sessions = listOf(session("a")))
        val sync = HealthConnectSleepSync(manager, store) { now }

        sync.sync()

        assertNull(store.token)
        assertEquals(emptyList<ExternalSleepSession>(), store.sessions)
        assertEquals(emptyList<ExternalSleepSession>(), sync.sessions.value)
    }

    @Test
    fun `never granted means no token, no read, nothing cached`() = runTest {
        val manager = mock<HealthConnectManager> { onBlocking { getChangesToken() } doReturn null }
        val store = MemoryStore()

        HealthConnectSleepSync(manager, store) { now }.sync()

        assertNull(store.token)
        verifyBlocking(manager, never()) { readSleepSessions(any(), any()) }
    }

    @Test
    fun `an upsert replaces the session with the same id`() {
        val result = HealthConnectSleepSync.applyChanges(
            listOf(session("a", 0)),
            SleepChanges.Changes(listOf(session("a", 0).copy(deepSleepMinutes = 42)), emptyList(), "t"),
        )
        assertEquals(42, result.single().deepSleepMinutes)
    }

    @Test
    fun `a refusal that isn't a revocation keeps the cache and token and reports failure`() = runTest {
        // e.g. a background read without background access: the manager rethrows because read
        // access is still granted. Wiping here would lose the user's synced nights.
        val manager = mock<HealthConnectManager> {
            onBlocking { getSleepChanges("t1") } doThrow SecurityException("background read not allowed")
        }
        val store = MemoryStore(token = "t1", sessions = listOf(session("a")))
        val sync = HealthConnectSleepSync(manager, store) { now }

        assertFalse(sync.sync())

        assertEquals("t1", store.token)
        assertEquals(listOf(session("a")), store.sessions)
        assertEquals(listOf(session("a")), sync.sessions.value)
    }
}
