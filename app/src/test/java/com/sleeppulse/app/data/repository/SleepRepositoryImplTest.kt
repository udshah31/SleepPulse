package com.sleeppulse.app.data.repository

import app.cash.turbine.test
import com.sleeppulse.app.data.local.NightlySummaryEntity
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.testutil.FakeNightlySummaryDao
import com.sleeppulse.app.testutil.FakeSensorDataSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepRepositoryImplTest {

    private fun summary(date: LocalDate, score: Int) = NightlySummary(
        date = date,
        sleepScore = score,
        avgHeartRateBpm = 58,
        avgHrvMillis = 72.5,
        totalSleepMinutes = 410,
        deepSleepMinutes = 95,
        remSleepMinutes = 105,
    )

    @Test
    fun `recordNightlySummary upserts then trims`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao)

        val date = LocalDate.of(2026, 7, 17)
        repository.recordNightlySummary(summary(date, score = 88))

        val stored = dao.entitiesFlow.value.single()
        assertEquals(date.toEpochDay(), stored.dateEpochDay)
        assertEquals(88, stored.sleepScore)
        assertEquals(58, stored.avgHeartRateBpm)
        assertEquals(72.5, stored.avgHrvMillis, 0.0001)
        assertEquals(410, stored.totalSleepMinutes)
        assertEquals(95, stored.deepSleepMinutes)
        assertEquals(105, stored.remSleepMinutes)
    }

    @Test
    fun `recentNights maps entities to domain objects with date round-trip`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao)

        val date = LocalDate.of(2026, 7, 10)
        dao.entitiesFlow.value = listOf(
            NightlySummaryEntity(
                dateEpochDay = date.toEpochDay(),
                sleepScore = 72,
                avgHeartRateBpm = 61,
                avgHrvMillis = 65.0,
                totalSleepMinutes = 400,
                deepSleepMinutes = 80,
                remSleepMinutes = 90,
            )
        )

        repository.recentNights().test {
            val nights = awaitItem()
            assertEquals(1, nights.size)
            assertEquals(date, nights[0].date)
            assertEquals(72, nights[0].sleepScore)
            assertEquals(61, nights[0].avgHeartRateBpm)
            assertEquals(65.0, nights[0].avgHrvMillis, 0.0001)
            assertEquals(400, nights[0].totalSleepMinutes)
            assertEquals(80, nights[0].deepSleepMinutes)
            assertEquals(90, nights[0].remSleepMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `liveReadings and connectionState pass through from the data source`() = runTest {
        val sensorDataSource = FakeSensorDataSource()
        val dao = FakeNightlySummaryDao()
        val repository = SleepRepositoryImpl(sensorDataSource, dao)

        repository.connectSensor()
        assertEquals(1, sensorDataSource.connectCallCount)

        repository.disconnectSensor()
        assertEquals(1, sensorDataSource.disconnectCallCount)

        repository.liveReadings().test {
            sensorDataSource.readingsFlow.emit(
                com.sleeppulse.app.data.model.SensorReading(
                    timestampMillis = 1L,
                    heartRateBpm = 62,
                    hrvMillis = 55.0,
                    sleepStage = com.sleeppulse.app.data.model.SleepStage.DEEP,
                )
            )
            val reading = awaitItem()
            assertEquals(62, reading.heartRateBpm)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
