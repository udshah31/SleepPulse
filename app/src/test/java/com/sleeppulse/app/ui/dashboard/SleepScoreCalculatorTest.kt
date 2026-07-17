package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.data.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScoreCalculatorTest {

    private fun reading(heartRateBpm: Int, hrvMillis: Double) = SensorReading(
        timestampMillis = 0L,
        heartRateBpm = heartRateBpm,
        hrvMillis = hrvMillis,
        sleepStage = SleepStage.LIGHT,
    )

    @Test
    fun `empty readings score zero`() {
        assertEquals(0, SleepScoreCalculator.score(emptyList()))
    }

    @Test
    fun `high hrv and low heart rate score near the top`() {
        val readings = listOf(reading(heartRateBpm = 50, hrvMillis = 100.0))
        val score = SleepScoreCalculator.score(readings)
        assertTrue("expected score >= 90, was $score", score >= 90)
    }

    @Test
    fun `low hrv and high heart rate score near the bottom`() {
        val readings = listOf(reading(heartRateBpm = 90, hrvMillis = 0.0))
        val score = SleepScoreCalculator.score(readings)
        assertTrue("expected score <= 10, was $score", score <= 10)
    }

    @Test
    fun `single reading produces a valid score`() {
        val readings = listOf(reading(heartRateBpm = 65, hrvMillis = 60.0))
        val score = SleepScoreCalculator.score(readings)
        assertTrue(score in 0..100)
    }

    @Test
    fun `extreme inputs stay clamped between 0 and 100`() {
        val extremeHigh = listOf(reading(heartRateBpm = 0, hrvMillis = 1000.0))
        val extremeLow = listOf(reading(heartRateBpm = 500, hrvMillis = -100.0))
        assertEquals(100, SleepScoreCalculator.score(extremeHigh))
        assertEquals(0, SleepScoreCalculator.score(extremeLow))
    }
}
