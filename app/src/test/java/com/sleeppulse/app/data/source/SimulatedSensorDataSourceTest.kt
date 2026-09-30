package com.sleeppulse.app.data.source

import android.content.Context
import app.cash.turbine.test
import com.sleeppulse.app.tracking.SleepStagePredictor
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock

class SimulatedSensorDataSourceTest {

    @Test
    fun `emits only while connected`() = runTest {
        val source = SimulatedSensorDataSource(SleepStagePredictor(mock<Context>()))

        source.readings().test {
            expectNoEvents()

            source.connect()
            awaitItem()

            source.disconnect()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
