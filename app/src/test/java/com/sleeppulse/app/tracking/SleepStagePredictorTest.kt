package com.sleeppulse.app.tracking

import com.sleeppulse.app.data.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SleepStagePredictorTest {

    private val predictor = SleepStagePredictor(mock())

    @Test
    fun `high movement predicts AWAKE regardless of hr or hrv`() {
        assertEquals(SleepStage.AWAKE, predictor.predict(heartRateBpm = 45, hrvMillis = 70, movement = 0.9f))
    }

    @Test
    fun `low heart rate and high hrv predicts DEEP`() {
        assertEquals(SleepStage.DEEP, predictor.predict(heartRateBpm = 45, hrvMillis = 65, movement = 0.1f))
    }

    @Test
    fun `mid heart rate and low hrv predicts REM`() {
        assertEquals(SleepStage.REM, predictor.predict(heartRateBpm = 58, hrvMillis = 30, movement = 0.1f))
    }

    @Test
    fun `everything else falls back to LIGHT`() {
        assertEquals(SleepStage.LIGHT, predictor.predict(heartRateBpm = 70, hrvMillis = 50, movement = 0.2f))
    }

    @Test
    fun `movement threshold is exclusive at 0_8`() {
        assertEquals(SleepStage.LIGHT, predictor.predict(heartRateBpm = 70, hrvMillis = 50, movement = 0.8f))
    }
}
