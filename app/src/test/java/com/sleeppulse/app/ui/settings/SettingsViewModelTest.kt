package com.sleeppulse.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `default state is simulated and celsius`() {
        val viewModel = SettingsViewModel()
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.CELSIUS, viewModel.state.value.temperatureUnit)
    }

    @Test
    fun `SetDataSource updates only dataSourceMode`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetDataSource(DataSourceMode.BLE))

        assertEquals(DataSourceMode.BLE, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.CELSIUS, viewModel.state.value.temperatureUnit)
    }

    @Test
    fun `SetTemperatureUnit updates only temperatureUnit`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetTemperatureUnit(TemperatureUnit.FAHRENHEIT))

        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.state.value.temperatureUnit)
        assertEquals(DataSourceMode.SIMULATED, viewModel.state.value.dataSourceMode)
    }

    @Test
    fun `both intents applied in sequence do not clobber each other`() {
        val viewModel = SettingsViewModel()
        viewModel.onIntent(SettingsIntent.SetDataSource(DataSourceMode.BLE))
        viewModel.onIntent(SettingsIntent.SetTemperatureUnit(TemperatureUnit.FAHRENHEIT))

        assertEquals(DataSourceMode.BLE, viewModel.state.value.dataSourceMode)
        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.state.value.temperatureUnit)
    }
}
