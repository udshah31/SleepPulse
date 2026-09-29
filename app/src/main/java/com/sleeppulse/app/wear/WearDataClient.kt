package com.sleeppulse.app.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sleeppulse.app.data.model.SensorReading
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WearDataClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // No paired watch / Wearable API missing: one failure means every later send fails too.
    @Volatile private var wearUnavailable = false

    fun sendReadingToWearable(reading: SensorReading) {
        if (wearUnavailable) return
        val dataClient = Wearable.getDataClient(context)
        val putDataMapReq = PutDataMapRequest.create("/sensor_data").apply {
            dataMap.putLong("timestamp", reading.timestampMillis)
            dataMap.putInt("heartRate", reading.heartRateBpm)
            dataMap.putDouble("hrv", reading.hrvMillis)
            dataMap.putString("sleepStage", reading.sleepStage.name)
        }
        
        val putDataReq = putDataMapReq.asPutDataRequest()
        putDataReq.setUrgent()
        
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            android.util.Log.d("SleepPulse", "Successfully sent data to wear: $it")
        }.addOnFailureListener {
            // Play services reports a missing Wearable API as API_NOT_CONNECTED (17) on the
            // ApiException; API_UNAVAILABLE (16) is only the inner ConnectionResult, so accept both.
            val code = (it as? ApiException)?.statusCode
            if (code == CommonStatusCodes.API_NOT_CONNECTED || code == ConnectionResult.API_UNAVAILABLE) {
                wearUnavailable = true
                android.util.Log.i("SleepPulse", "Wearable API unavailable; not syncing readings to watch this session")
            } else {
                android.util.Log.e("SleepPulse", "Failed to send data to wear", it)
            }
        }
    }
}
