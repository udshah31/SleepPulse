package com.sleeppulse.app.data.source

import com.sleeppulse.app.data.model.SensorConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MockitoTest {

    @Test
    fun testMock() {
        val simulatedConnectionState = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
        val mockSimulated = mock<SimulatedSensorDataSource> {
            on { connectionState } doReturn simulatedConnectionState
        }
        
        println("Mock simulated: ${mockSimulated.connectionState}")
    }
}
