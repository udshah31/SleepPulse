package com.sleeppulse.app.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
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
            if ((it as? ApiException)?.statusCode == ConnectionResult.API_UNAVAILABLE) {
                wearUnavailable = true
                android.util.Log.i("SleepPulse", "Wearable API unavailable; not syncing readings to watch this session")
            } else {
                android.util.Log.e("SleepPulse", "Failed to send data to wear", it)
            }
        }
    }
}
