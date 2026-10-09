package com.sleeppulse.shared.sensor

import com.sleeppulse.shared.model.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSimulatedSensorDataSourceTest {
    @Test fun `one producer uses actual clock for all observers and stops`() = runTest {
        var clock = 123_000L
        val sensor = RealtimeSimulatedSensorDataSource(backgroundScope) { clock }
        val first = mutableListOf<SensorReading>()
        val second = mutableListOf<SensorReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sensor.readings().toList(first) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sensor.readings().toList(second) }
        runCurrent()
        assertTrue(first.isEmpty())
        sensor.connect()
        sensor.connect()
        runCurrent()
        clock = 128_000L // A delayed wall clock is not synthesized from ticks.
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(123_000L, 128_000L), first.map { it.timestampMillis })
        assertEquals(first, second)
        sensor.disconnect()
        advanceTimeBy(3_000)
        assertEquals(2, first.size)
        sensor.connect()
        runCurrent()
        assertEquals(first.first().heartRateBpm, first.last().heartRateBpm)
        assertEquals(first.first().sleepStage, first.last().sleepStage)
        sensor.disconnect()
    }
}
