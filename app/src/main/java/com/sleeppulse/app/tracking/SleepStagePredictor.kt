package com.sleeppulse.app.tracking

import android.content.Context
import com.sleeppulse.app.data.model.SleepStage

class SleepStagePredictor(private val context: Context) {

    fun predict(heartRateBpm: Int, hrvMillis: Long?, movement: Float): SleepStage {
        return when {
            movement > 0.8f -> SleepStage.AWAKE
            hrvMillis != null && heartRateBpm < 50 && hrvMillis > 60 -> SleepStage.DEEP
            hrvMillis != null && heartRateBpm in 50..65 && hrvMillis < 40 -> SleepStage.REM
            else -> SleepStage.LIGHT
        }
    }
}
