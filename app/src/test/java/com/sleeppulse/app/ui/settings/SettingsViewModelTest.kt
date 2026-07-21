package com.sleeppulse.app.ui.settings

import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.notifications.WindDownScheduler
import com.sleeppulse.app.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import com.sleeppulse.app.notifications.SmartAlarmScheduler

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockWindDownScheduler = mock<WindDownScheduler>()
    private val mockSmartAlarmScheduler = mock<SmartAlarmScheduler>()

    @Test
    fun `default state is simulated, celsius, and 22_30 bedtime`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        advanceUntilIdle()
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.CELSIUS, viewModel.state.value.temperatureUnit)
        assertEquals(22, viewModel.state.value.targetBedtimeHour)
        assertEquals(30, viewModel.state.value.targetBedtimeMinute)
    }

    @Test
    fun `SetDataSource updates only dataSourceMode`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        viewModel.onIntent(SettingsIntent.SetDataSource(DataSourceMode.BLE))
        advanceUntilIdle()

        assertEquals(DataSourceMode.BLE, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.CELSIUS, viewModel.state.value.temperatureUnit)
    }

    @Test
    fun `SetTemperatureUnit updates only temperatureUnit`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        viewModel.onIntent(SettingsIntent.SetTemperatureUnit(TemperatureUnit.FAHRENHEIT))
        advanceUntilIdle()

        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.state.value.temperatureUnit)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
    }

    @Test
    fun `both intents applied in sequence do not clobber each other`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        viewModel.onIntent(SettingsIntent.SetDataSource(DataSourceMode.BLE))
        viewModel.onIntent(SettingsIntent.SetTemperatureUnit(TemperatureUnit.FAHRENHEIT))
        advanceUntilIdle()

        assertEquals(DataSourceMode.BLE, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.state.value.temperatureUnit)
    }

    @Test
    fun `SetSelectedBleDevice updates only selectedBleDeviceLabel`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        viewModel.onIntent(SettingsIntent.SetSelectedBleDevice("Fake HR Strap (AA:BB)"))
        advanceUntilIdle()

        assertEquals("Fake HR Strap (AA:BB)", viewModel.state.value.selectedBleDeviceLabel)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
    }

    @Test
    fun `SetTargetBedtime updates only targetBedtime and schedules reminder`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        viewModel.onIntent(SettingsIntent.SetTargetBedtime(23, 15))
        advanceUntilIdle()

        assertEquals(23, viewModel.state.value.targetBedtimeHour)
        assertEquals(15, viewModel.state.value.targetBedtimeMinute)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
        
        verify(mockWindDownScheduler).scheduleWindDown(23, 15)
    }

    @Test
    fun `SetTargetWakeup updates targetWakeup and schedules smart alarm`() = runTest {
        val viewModel = SettingsViewModel(SettingsRepository(), mockWindDownScheduler, mockSmartAlarmScheduler)
        viewModel.onIntent(SettingsIntent.SetTargetWakeup(6, 30, 30))
        advanceUntilIdle()

        assertEquals(6, viewModel.state.value.targetWakeupHour)
        assertEquals(30, viewModel.state.value.targetWakeupMinute)
        assertEquals(30, viewModel.state.value.wakeWindowMinutes)
        
        verify(mockSmartAlarmScheduler).scheduleHardAlarm(6, 30)
    }
}
