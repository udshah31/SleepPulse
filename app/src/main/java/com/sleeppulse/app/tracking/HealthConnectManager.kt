package com.sleeppulse.app.tracking

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.data.model.StageSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

class HealthConnectManager(private val context: Context) {

    companion object {
        /**
         * Health Connect permissions aren't plain manifest strings — the UI layer needs this
         * same set to drive [androidx.health.connect.client.PermissionController]'s request
         * contract, so it's exposed here rather than duplicated.
         */
        private val WRITE_SLEEP = HealthPermission.getWritePermission(SleepSessionRecord::class)
        private val WRITE_HEART_RATE = HealthPermission.getWritePermission(HeartRateRecord::class)

        /** Every write we ask for. Each write checks only its own permission, so a partial grant still syncs what it can. */
        val REQUIRED_PERMISSIONS: Set<String> = setOf(WRITE_SLEEP, WRITE_HEART_RATE)

        /** Read access, requested alongside write but checked separately so writes never depend on it. */
        val READ_PERMISSIONS: Set<String> =
            setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

        /**
         * Lets [com.sleeppulse.app.tracking.SleepSyncWorker] read while the app isn't visible.
         * Health Connect only offers it on devices that support background reads; without it,
         * background reads throw and the worker just skips.
         */
        const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        val REQUESTED_PERMISSIONS: Set<String> = REQUIRED_PERMISSIONS + READ_PERMISSIONS + READ_IN_BACKGROUND

        /**
         * Keeps sleep sessions written by other apps (ours are already in Room) and reduces each
         * to what the app uses. Deep/REM minutes come from the record's stages; a record with no
         * stages reports 0 for both rather than guessing.
         */
        fun fromOtherApps(records: List<SleepSessionRecord>, ownPackage: String): List<ExternalSleepSession> =
            records
                .filter { it.metadata.dataOrigin.packageName != ownPackage }
                .map { r ->
                    fun minutesOf(type: Int) = r.stages.filter { it.stage == type }
                        .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                    ExternalSleepSession(
                        id = r.metadata.id,
                        startMillis = r.startTime.toEpochMilli(),
                        endMillis = r.endTime.toEpochMilli(),
                        deepSleepMinutes = minutesOf(SleepSessionRecord.STAGE_TYPE_DEEP),
                        remSleepMinutes = minutesOf(SleepSessionRecord.STAGE_TYPE_REM),
                        sourcePackage = r.metadata.dataOrigin.packageName,
                    )
                }

        /**
         * Builds the record Health Connect stores for a night, or null if there is no positive
         * duration (Health Connect rejects end <= start). The end comes from the last stage
         * segment when there are any, else start + total minutes; segments are clamped into
         * the session so a stray timestamp can't make the whole insert fail.
         */
        fun buildSleepRecord(
            summary: NightlySummary,
            stages: List<StageSegment>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): SleepSessionRecord? {
            val startMillis = summary.bedtimeEpochMillis
            val endMillis = stages.lastOrNull()?.endMillis
                ?: (startMillis + summary.totalSleepMinutes * 60_000L)
            if (startMillis <= 0L || endMillis <= startMillis) return null

            val start = Instant.ofEpochMilli(startMillis)
            val end = Instant.ofEpochMilli(endMillis)
            return SleepSessionRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = end,
                endZoneOffset = zone.rules.getOffset(end),
                stages = stages.mapNotNull { seg ->
                    val s = maxOf(seg.startMillis, startMillis)
                    val e = minOf(seg.endMillis, endMillis)
                    if (e <= s) null
                    else SleepSessionRecord.Stage(Instant.ofEpochMilli(s), Instant.ofEpochMilli(e), seg.stage.toHcStage())
                },
                metadata = Metadata(clientRecordId = "sleeppulse-$startMillis"),
            )
        }

        /**
         * One heart-rate record for the night, sampled once per minute (the mean of that minute's
         * readings) — the sensor ticks about once a second, far denser than any consumer of this
         * data needs. Readings outside Health Connect's accepted 1..300 bpm (sensor dropouts read
         * as 0) are dropped; one bad sample would otherwise fail the whole insert. Null when
         * nothing valid remains or the span has no duration.
         */
        // ponytail: one record per night — an 8h night is ~480 samples; split into hourly records if sessions ever run far longer
        fun buildHeartRateRecord(
            readings: List<SensorReading>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): HeartRateRecord? {
            val valid = readings.filter { it.heartRateBpm in 1..300 }
            if (valid.size < 2) return null
            val startMillis = valid.first().timestampMillis
            val endMillis = valid.last().timestampMillis
            if (endMillis <= startMillis) return null

            val samples = valid
                .groupBy { (it.timestampMillis - startMillis) / 60_000L }
                .toSortedMap()
                .map { (_, minute) ->
                    HeartRateRecord.Sample(
                        time = Instant.ofEpochMilli(minute.first().timestampMillis),
                        beatsPerMinute = minute.map { it.heartRateBpm }.average().roundToLong(),
                    )
                }
            val start = Instant.ofEpochMilli(startMillis)
            val end = Instant.ofEpochMilli(endMillis)
            return HeartRateRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = end,
                endZoneOffset = zone.rules.getOffset(end),
                samples = samples,
                metadata = Metadata(clientRecordId = "sleeppulse-hr-$startMillis"),
            )
        }

        private fun SleepStage.toHcStage() = when (this) {
            SleepStage.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
            SleepStage.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
            SleepStage.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
            SleepStage.REM -> SleepSessionRecord.STAGE_TYPE_REM
        }
    }

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasRequiredPermissions(): Boolean = hasPermission(*REQUIRED_PERMISSIONS.toTypedArray())

    private suspend fun hasPermission(vararg permissions: String): Boolean {
        if (!isAvailable()) return false
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions.toList())
    }

    suspend fun canReadInBackground(): Boolean =
        hasPermission(*(READ_PERMISSIONS + READ_IN_BACKGROUND).toTypedArray())

    /**
     * A SecurityException means "revoked" only if read access is really gone; otherwise it's a
     * refusal for another reason (e.g. a background read without background access) and must
     * not be mistaken for a revocation, which would wipe the synced cache.
     */
    private suspend fun revokedOrRethrow(e: SecurityException) {
        if (hasReadPermissions()) throw e
    }

    suspend fun hasReadPermissions(): Boolean {
        if (!isAvailable()) return false
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(READ_PERMISSIONS)
    }

    /**
     * Sleep sessions other apps (a watch, Samsung Health, ...) wrote between [from] and [to].
     * Returns empty when Health Connect is unavailable or read access isn't granted. Follows
     * page tokens so a long range isn't silently cut at the first page.
     */
    suspend fun readSleepSessions(from: Instant, to: Instant): List<ExternalSleepSession> {
        if (!hasReadPermissions()) return emptyList()
        return withContext(Dispatchers.IO) {
            val records = mutableListOf<SleepSessionRecord>()
            var pageToken: String? = null
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken,
                    )
                )
                records += response.records
                pageToken = response.pageToken
            } while (pageToken != null)
            fromOtherApps(records, context.packageName)
        }
    }

    /**
     * A token marking "now" in Health Connect's change log for sleep sessions, or null without
     * read access. Take it *before* a full read so nothing written during the read is missed —
     * a change seen by both is just an idempotent upsert.
     */
    suspend fun getChangesToken(): String? {
        if (!hasReadPermissions()) return null
        return try {
            withContext(Dispatchers.IO) {
                healthConnectClient.getChangesToken(ChangesTokenRequest(setOf(SleepSessionRecord::class)))
            }
        } catch (e: SecurityException) {
            revokedOrRethrow(e)
            null // revoked between the check and the call
        }
    }

    /** Everything that changed since [token], draining all pages (`hasMore`). */
    suspend fun getSleepChanges(token: String): SleepChanges {
        if (!hasReadPermissions()) return SleepChanges.NoPermission
        return try {
            withContext(Dispatchers.IO) {
                val upserted = mutableListOf<SleepSessionRecord>()
                val deletedIds = mutableListOf<String>()
                var next = token
                do {
                    val response = healthConnectClient.getChanges(next)
                    // Tokens expire (~30 days unused); the caller must fall back to a full read.
                    if (response.changesTokenExpired) return@withContext SleepChanges.TokenExpired
                    response.changes.forEach { change ->
                        when (change) {
                            is UpsertionChange -> (change.record as? SleepSessionRecord)?.let(upserted::add)
                            is DeletionChange -> deletedIds += change.recordId
                        }
                    }
                    next = response.nextChangesToken
                } while (response.hasMore)
                SleepChanges.Changes(fromOtherApps(upserted, context.packageName), deletedIds, next)
            }
        } catch (e: SecurityException) {
            revokedOrRethrow(e)
            SleepChanges.NoPermission
        }
    }

    suspend fun writeSleepSession(summary: NightlySummary, stages: List<StageSegment> = emptyList()) {
        if (!hasPermission(WRITE_SLEEP)) {
            android.util.Log.w("HealthConnectManager", "Skipping write: Health Connect permission not granted")
            return
        }
        val record = buildSleepRecord(summary, stages) ?: run {
            android.util.Log.w("HealthConnectManager", "Skipping write: session has no positive duration")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Same clientRecordId => Health Connect upserts, so a retried finalize can't duplicate.
                healthConnectClient.insertRecords(listOf(record))
            } catch (e: Exception) {
                android.util.Log.e("HealthConnectManager", "Error writing sleep session", e)
            }
        }
    }

    suspend fun writeHeartRate(readings: List<SensorReading>) {
        if (!hasPermission(WRITE_HEART_RATE)) {
            android.util.Log.w("HealthConnectManager", "Skipping heart-rate write: permission not granted")
            return
        }
        val record = buildHeartRateRecord(readings) ?: return
        withContext(Dispatchers.IO) {
            try {
                healthConnectClient.insertRecords(listOf(record))
            } catch (e: Exception) {
                android.util.Log.e("HealthConnectManager", "Error writing heart rate", e)
            }
        }
    }
}

/** A night recorded by another app, as read back from Health Connect. */
data class ExternalSleepSession(
    /** Health Connect's record id — the key change/deletion events refer to. */
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
    val sourcePackage: String,
)

sealed interface SleepChanges {
    /** [upserted] is already filtered to other apps; [deletedIds] may include any app's ids. */
    data class Changes(
        val upserted: List<ExternalSleepSession>,
        val deletedIds: List<String>,
        val nextToken: String,
    ) : SleepChanges

    data object TokenExpired : SleepChanges
    data object NoPermission : SleepChanges
}
