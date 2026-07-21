package com.sleeppulse.app.ui.settings

enum class DataSourceMode { SIMULATED, BLE }
enum class HeartRateUnit { BPM }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

sealed class SettingsIntent {
    data class SetDataSource(val mode: DataSourceMode) : SettingsIntent()
    data class SetTemperatureUnit(val unit: TemperatureUnit) : SettingsIntent()
    data class SetSelectedBleDevice(val label: String) : SettingsIntent()
}

data class SettingsState(
    val dataSourceMode: DataSourceMode = DataSourceMode.SIMULATED,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val selectedBleDeviceLabel: String? = null,
)
