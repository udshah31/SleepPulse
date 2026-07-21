package com.sleeppulse.app.tracking

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.data.model.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    suspend fun writeSleepSession(summary: NightlySummary) {
        if (!isAvailable()) return

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

    private fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }
}
