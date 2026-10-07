package com.sleeppulse.app.data.source

import app.cash.turbine.test
import com.sleeppulse.shared.model.SensorConnectionState
import com.sleeppulse.shared.model.SensorReading
import com.sleeppulse.shared.model.SleepStage
import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.testutil.MainDispatcherRule
import com.sleeppulse.app.tracking.PhoneMovement
import com.sleeppulse.app.tracking.SleepStagePredictor
import com.sleeppulse.app.ui.settings.DataSourceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

class SensorSourceManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `connectionState routes to Simulated by default and switches when mode changes`() = runTest {
        val repo = SettingsRepository()
        val mockContext = mock<android.content.Context>()
        val simulated = SimulatedSensorDataSource(mock<SleepStagePredictor>())
        val ble = BleSensorDataSource(mockContext, mock<SleepStagePredictor>(), PhoneMovement())
        val manager = SensorSourceManager(repo, simulated, ble)

        manager.connectionState.test {
            // Simulated source initializes as Disconnected
            assertEquals(SensorConnectionState.Disconnected, awaitItem())

            // Switch to BLE
            repo.setDataSourceMode(DataSourceMode.BLE)
            
            // BLE source initializes as Disconnected, so we should get another Disconnected emission
            // because flatMapLatest switches to the new flow which emits its initial value.
            assertEquals(SensorConnectionState.Disconnected, awaitItem())

            // Connect BLE source directly. In a unit test context without Robolectric, 
            // BluetoothAdapter.getDefaultAdapter() returns null or isn't available, 
            // but more importantly we haven't set a target device, so it immediately 
            // emits an Error state.
            val job = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Default) { ble.connect() }
            
            val errorState = awaitItem()
            org.junit.Assert.assertTrue("Expected Error state but got $errorState", errorState is SensorConnectionState.Error)
            cancelAndIgnoreRemainingEvents()
            job.cancel()
        }
    }

    @Test
    fun `connect routes to active source`() = runTest {
        val repo = SettingsRepository()
        val mockContext = mock<android.content.Context>()
        val simulated = SimulatedSensorDataSource(mock<SleepStagePredictor>())
        val ble = BleSensorDataSource(mockContext, mock<SleepStagePredictor>(), PhoneMovement())
        val manager = SensorSourceManager(repo, simulated, ble)

        // Default is SIMULATED
        manager.connectionState.test {
            assertEquals(SensorConnectionState.Disconnected, awaitItem())
            
            val job = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Default) { manager.connect() }
            assertEquals(SensorConnectionState.Connecting, awaitItem())
            assertEquals(SensorConnectionState.Connected("Simulated Sensor"), awaitItem())
            cancelAndIgnoreRemainingEvents()
            job.cancel()
        }

        // Switch to BLE
        repo.setDataSourceMode(DataSourceMode.BLE)
        manager.connect()
        // BleSensorDataSource throws Error("No BLE device selected") if connected without target
        ble.connectionState.test {
            val state = awaitItem()
            assert(state is SensorConnectionState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect disconnects both sources`() = runTest {
        val repo = SettingsRepository()
        val mockContext = mock<android.content.Context>()
        val simulated = SimulatedSensorDataSource(mock<SleepStagePredictor>())
        val ble = BleSensorDataSource(mockContext, mock<SleepStagePredictor>(), PhoneMovement())
        val manager = SensorSourceManager(repo, simulated, ble)

        manager.connect()
        repo.setDataSourceMode(DataSourceMode.BLE)
        ble.setTargetDevice("AA:BB:CC:DD:EE:FF")
        
        manager.disconnect()
        
        simulated.connectionState.test {
            assertEquals(SensorConnectionState.Disconnected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        ble.connectionState.test {
            assertEquals(SensorConnectionState.Disconnected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BLE connect after a restart hands the saved device back to the BLE source`() = runTest {
        val store = com.sleeppulse.app.data.repository.InMemorySettingsStore()
        SettingsRepository(store).apply {
            setDataSourceMode(DataSourceMode.BLE)
            setBleDevice("AA:BB:CC:DD:EE:FF", "Strap (AA:BB:CC:DD:EE:FF)")
        }
        val ble = mock<BleSensorDataSource>()
        val manager = SensorSourceManager(SettingsRepository(store), SimulatedSensorDataSource(mock<SleepStagePredictor>()), ble)

        manager.connect()

        verify(ble).setTargetDevice("AA:BB:CC:DD:EE:FF")
        verifyBlocking(ble) { connect() }
    }

    @Test
    fun `simulated connect never touches the BLE target`() = runTest {
        val repo = SettingsRepository().apply { setBleDevice("AA:BB", "x") }
        val ble = mock<BleSensorDataSource>()
        val manager = SensorSourceManager(repo, SimulatedSensorDataSource(mock<SleepStagePredictor>()), ble)

        val job = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Default) { manager.connect() }
        job.cancel()

        verify(ble, never()).setTargetDevice(any())
    }
}
