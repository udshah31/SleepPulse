package com.sleeppulse.app.data.repository

import com.sleeppulse.app.ui.settings.DataSourceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global holder for app settings.
 * Currently uses an in-memory [MutableStateFlow] defaulting to [DataSourceMode.SIMULATED].
 */
@Singleton
class SettingsRepository @Inject constructor() {

    private val _dataSourceMode = MutableStateFlow(DataSourceMode.SIMULATED)
    val dataSourceMode: StateFlow<DataSourceMode> = _dataSourceMode.asStateFlow()

    private val _targetBedtimeHour = MutableStateFlow(22) // Default 10 PM
    val targetBedtimeHour: StateFlow<Int> = _targetBedtimeHour.asStateFlow()

    private val _targetBedtimeMinute = MutableStateFlow(30) // Default 10:30 PM
    val targetBedtimeMinute: StateFlow<Int> = _targetBedtimeMinute.asStateFlow()

    private val _targetWakeupHour = MutableStateFlow(7) // Default 7 AM
    val targetWakeupHour: StateFlow<Int> = _targetWakeupHour.asStateFlow()

    private val _targetWakeupMinute = MutableStateFlow(0) // Default 7:00 AM
    val targetWakeupMinute: StateFlow<Int> = _targetWakeupMinute.asStateFlow()

    private val _wakeWindowMinutes = MutableStateFlow(30) // Default 30 min window
    val wakeWindowMinutes: StateFlow<Int> = _wakeWindowMinutes.asStateFlow()

    private val _amoledBlack = MutableStateFlow(false)
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    fun setDataSourceMode(mode: DataSourceMode) {
        _dataSourceMode.value = mode
    }

    fun setTargetBedtime(hour: Int, minute: Int) {
        _targetBedtimeHour.value = hour
        _targetBedtimeMinute.value = minute
    }

    fun setTargetWakeup(hour: Int, minute: Int, windowMinutes: Int) {
        _targetWakeupHour.value = hour
        _targetWakeupMinute.value = minute
        _wakeWindowMinutes.value = windowMinutes
    }

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
    }
}
