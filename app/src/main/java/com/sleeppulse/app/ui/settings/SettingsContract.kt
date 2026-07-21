package com.sleeppulse.app.ui.settings

enum class DataSourceMode { SIMULATED, BLE }
enum class HeartRateUnit { BPM }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

sealed class SettingsIntent {
    data class SetDataSource(val mode: DataSourceMode) : SettingsIntent()
    data class SetTemperatureUnit(val unit: TemperatureUnit) : SettingsIntent()
    data class SetSelectedBleDevice(val label: String) : SettingsIntent()
    data class SetTargetBedtime(val hour: Int, val minute: Int) : SettingsIntent()
    data class SetTargetWakeup(val hour: Int, val minute: Int, val windowMinutes: Int) : SettingsIntent()
    data class SetAmoledBlack(val enabled: Boolean) : SettingsIntent()
}

data class SettingsState(
    val dataSourceMode: DataSourceMode = DataSourceMode.SIMULATED,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val selectedBleDeviceLabel: String? = null,
    val targetBedtimeHour: Int = 22,
    val targetBedtimeMinute: Int = 30,
    val targetWakeupHour: Int = 7,
    val targetWakeupMinute: Int = 0,
    val wakeWindowMinutes: Int = 30,
    val amoledBlack: Boolean = false,
)
