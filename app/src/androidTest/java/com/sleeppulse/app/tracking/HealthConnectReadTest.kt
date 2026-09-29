package com.sleeppulse.app.tracking

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real Health Connect read against the device. Needs READ_SLEEP/WRITE_SLEEP granted
 * first (adb shell pm grant com.sleeppulse.app android.permission.health.READ_SLEEP ...).
 * Records written here come from this app, so readSleepSessions must filter them out; the
 * other-app branch is covered by the JVM unit test.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectReadTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = HealthConnectManager(context)
    private val client = HealthConnectClient.getOrCreate(context)

    @Test
    fun readPathSeesTheRecordButFiltersOutOurOwnWrite() = runBlocking {
        assertTrue("Health Connect unavailable", manager.isAvailable())
        assertTrue("READ_SLEEP not granted", manager.hasReadPermissions())

        val start = Instant.now().minusSeconds(10 * 3600)
        val end = start.plusSeconds(7 * 3600)
        val id = "sleeppulse-readtest-${start.toEpochMilli()}"
        client.insertRecords(
            listOf(SleepSessionRecord(start, null, end, null, metadata = Metadata(clientRecordId = id)))
        )

        val raw = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start.minusSeconds(60), end.plusSeconds(60)))
        ).records
        assertTrue("raw read did not return the inserted record", raw.any { it.metadata.clientRecordId == id })

        val external = manager.readSleepSessions(start.minusSeconds(60), end.plusSeconds(60))
        assertTrue("own record leaked into readSleepSessions", external.none { it.startMillis == start.toEpochMilli() })
    }

    @Test
    fun changesTokenSeesAnInsertThenItsDeletion() = runBlocking {
        val token = manager.getChangesToken()
        assertTrue("no changes token (read access missing?)", token != null)

        val start = Instant.now().minusSeconds(20 * 3600)
        val inserted = client.insertRecords(
            listOf(SleepSessionRecord(start, null, start.plusSeconds(3600), null))
        ).recordIdsList.single()

        val afterInsert = manager.getSleepChanges(token!!)
        assertTrue("expected Changes, got $afterInsert", afterInsert is SleepChanges.Changes)
        afterInsert as SleepChanges.Changes
        // Our own write shows up in the change log but is filtered out of the upserts.
        assertTrue(afterInsert.upserted.none { it.id == inserted })

        client.deleteRecords(SleepSessionRecord::class, listOf(inserted), emptyList())
        val afterDelete = manager.getSleepChanges(afterInsert.nextToken)
        assertTrue("expected Changes, got $afterDelete", afterDelete is SleepChanges.Changes)
        assertTrue("deletion not reported", (afterDelete as SleepChanges.Changes).deletedIds.contains(inserted))
    }
}
