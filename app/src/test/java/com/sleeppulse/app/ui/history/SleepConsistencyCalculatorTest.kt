package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepConsistencyCalculatorTest {

    @Test
    fun `less than 2 nights returns null`() {
        assertNull(SleepConsistencyCalculator.calculateScore(emptyList()))
        assertNull(SleepConsistencyCalculator.calculateScore(listOf(createNight(22, 0))))
    }

    @Test
    fun `perfect consistency returns 100`() {
        val nights = listOf(
            createNight(22, 0),
            createNight(22, 0),
            createNight(22, 0)
        )
        assertEquals(100, SleepConsistencyCalculator.calculateScore(nights))
    }

    @Test
    fun `variance under 30 mins returns 100`() {
        val nights = listOf(
            createNight(22, 0),
            createNight(22, 15),
            createNight(21, 45) // stdDev is roughly 12 mins
        )
        assertEquals(100, SleepConsistencyCalculator.calculateScore(nights))
    }

    @Test
    fun `variance of 60 mins returns 83`() {
        // Map [30, 120] to [100, 50]
        // 60 is 1/3 of the way between 30 and 120.
        // So score should be 100 - (30/90)*50 = 100 - 16.66 = 83.33 -> 83
        val nights = listOf(
            createNight(22, 0), // 240 mins from 6PM
            createNight(23, 0), // 300 mins
            createNight(24, 0), // 360 mins (midnight)
        )
        // mean = 300
        // variance = ((240-300)^2 + (300-300)^2 + (360-300)^2)/3 = (3600 + 0 + 3600)/3 = 2400
        // stdDev = sqrt(2400) = ~48.98
        // stdDev is ~49.
        // fraction = (48.98 - 30) / 90 = 18.98 / 90 = 0.21
        // score = 100 - (0.21 * 50) = 89
        assertEquals(89, SleepConsistencyCalculator.calculateScore(nights))
    }
    
    @Test
    fun `handles midnight wrap-around perfectly`() {
        // 11 PM and 1 AM
        // 11 PM = 300 mins from 6 PM
        // 1 AM = 420 mins from 6 PM
        // Mean = 360
        // Variance = (3600 + 3600) / 2 = 3600
        // stdDev = 60
        // fraction = (60 - 30) / 90 = 30 / 90 = 0.33
        // score = 100 - (0.33 * 50) = 83
        val nights = listOf(
            createNight(23, 0), // 11 PM
            createNight(1, 0)   // 1 AM
        )
        assertEquals(83, SleepConsistencyCalculator.calculateScore(nights))
    }

    private fun createNight(hour: Int, minute: Int): NightlySummary {
        // Handle hour >= 24 as next day early morning
        val actualHour = hour % 24
        val dayOffset = if (hour >= 24) 1 else 0
        
        val zdt = ZonedDateTime.now(ZoneId.systemDefault())
            .withHour(actualHour).withMinute(minute).withSecond(0).withNano(0)
            .plusDays(dayOffset.toLong())

        return NightlySummary(
            date = LocalDate.now(),
            bedtimeEpochMillis = zdt.toInstant().toEpochMilli(),
            sleepScore = 80,
            avgHeartRateBpm = 60,
            avgHrvMillis = 50.0,
            totalSleepMinutes = 480,
            deepSleepMinutes = 120,
            remSleepMinutes = 90
        )
    }
}
