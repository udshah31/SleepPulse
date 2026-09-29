package com.sleeppulse.app.tracking

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SleepStage
import com.sleeppulse.app.data.model.StageSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class HealthConnectManager(private val context: Context) {

    companion object {
        /**
         * Health Connect permissions aren't plain manifest strings — the UI layer needs this
         * same set to drive [androidx.health.connect.client.PermissionController]'s request
         * contract, so it's exposed here rather than duplicated.
         */
        val REQUIRED_PERMISSIONS: Set<String> =
            setOf(HealthPermission.getWritePermission(SleepSessionRecord::class))

        /** Read access, requested alongside write but checked separately so writes never depend on it. */
        val READ_PERMISSIONS: Set<String> =
            setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

        val REQUESTED_PERMISSIONS: Set<String> = REQUIRED_PERMISSIONS + READ_PERMISSIONS

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

    suspend fun hasRequiredPermissions(): Boolean {
        if (!isAvailable()) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
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

    suspend fun writeSleepSession(summary: NightlySummary, stages: List<StageSegment> = emptyList()) {
        if (!hasRequiredPermissions()) {
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
}

/** A night recorded by another app, as read back from Health Connect. */
data class ExternalSleepSession(
    val startMillis: Long,
    val endMillis: Long,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
    val sourcePackage: String,
)
