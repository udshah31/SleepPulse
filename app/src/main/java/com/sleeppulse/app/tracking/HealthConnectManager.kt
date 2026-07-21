package com.sleeppulse.app.tracking

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class HealthConnectManager(private val context: Context) {

    companion object {
        /**
         * Health Connect permissions aren't plain manifest strings — the UI layer needs this
         * same set to drive [androidx.health.connect.client.PermissionController]'s request
         * contract, so it's exposed here rather than duplicated.
         */
        val REQUIRED_PERMISSIONS: Set<String> =
            setOf(HealthPermission.getWritePermission(SleepSessionRecord::class))
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

    suspend fun writeSleepSession(summary: NightlySummary) {
        if (!hasRequiredPermissions()) {
            android.util.Log.w("HealthConnectManager", "Skipping write: Health Connect permission not granted")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Determine the total duration
                val startTime = Instant.ofEpochMilli(summary.bedtimeEpochMillis)
                val endTime = startTime.plusSeconds(summary.totalSleepMinutes * 60L)

                // Create the overall session
                val sleepSession = SleepSessionRecord(
                    startTime = startTime,
                    endTime = endTime,
                    startZoneOffset = null,
                    endZoneOffset = null
                )

                healthConnectClient.insertRecords(listOf(sleepSession))

            } catch (e: Exception) {
                android.util.Log.e("HealthConnectManager", "Error writing sleep session", e)
            }
        }
    }
}
