package com.sleeppulse.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.notifications.SmartAlarmScheduler
import com.sleeppulse.app.notifications.WindDownScheduler
import com.sleeppulse.app.widget.SleepPulseWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages UI state for the Settings screen.
 * The data-source mode is now persisted in [SettingsRepository] and triggers a live
 * hot-swap of the underlying sensor via [com.sleeppulse.app.data.source.SensorSourceManager].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val windDownScheduler: WindDownScheduler,
    private val smartAlarmScheduler: SmartAlarmScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.dataSourceMode.collect { mode ->
                _state.update { it.copy(dataSourceMode = mode) }
            }
        }
        viewModelScope.launch {
            repository.targetBedtimeHour.collect { hour ->
                _state.update { it.copy(targetBedtimeHour = hour) }
            }
        }
        viewModelScope.launch {
            repository.targetBedtimeMinute.collect { minute ->
                _state.update { it.copy(targetBedtimeMinute = minute) }
            }
        }
        viewModelScope.launch {
            repository.targetWakeupHour.collect { hour ->
                _state.update { it.copy(targetWakeupHour = hour) }
            }
        }
        viewModelScope.launch {
            repository.targetWakeupMinute.collect { minute ->
                _state.update { it.copy(targetWakeupMinute = minute) }
            }
        }
        viewModelScope.launch {
            repository.wakeWindowMinutes.collect { window ->
                _state.update { it.copy(wakeWindowMinutes = window) }
            }
        }
        viewModelScope.launch {
            repository.amoledBlack.collect { enabled ->
                _state.update { it.copy(amoledBlack = enabled) }
            }
        }
        viewModelScope.launch {
            repository.phoneMovementEnabled.collect { enabled ->
                _state.update { it.copy(phoneMovementEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            repository.temperatureUnit.collect { unit ->
                _state.update { it.copy(temperatureUnit = unit) }
            }
        }
        viewModelScope.launch {
            // Restores the chosen sensor's label after a restart; the Scan screen saves it.
            repository.bleDeviceLabel.collect { label ->
                if (label != null) _state.update { it.copy(selectedBleDeviceLabel = label) }
            }
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetDataSource -> repository.setDataSourceMode(intent.mode)
            is SettingsIntent.SetTemperatureUnit -> repository.setTemperatureUnit(intent.unit)
            is SettingsIntent.SetSelectedBleDevice -> _state.update { it.copy(selectedBleDeviceLabel = intent.label) }
            is SettingsIntent.SetTargetBedtime -> {
                repository.setTargetBedtime(intent.hour, intent.minute)
                windDownScheduler.scheduleWindDown(intent.hour, intent.minute)
                viewModelScope.launch { SleepPulseWidget.refresh(context) }
            }
            is SettingsIntent.SetTargetWakeup -> {
                repository.setTargetWakeup(intent.hour, intent.minute, intent.windowMinutes)
                smartAlarmScheduler.scheduleHardAlarm(intent.hour, intent.minute)
            }
            is SettingsIntent.SetAmoledBlack -> {
                repository.setAmoledBlack(intent.enabled)
            }
            is SettingsIntent.SetPhoneMovementEnabled -> repository.setPhoneMovementEnabled(intent.enabled)
        }
    }
}
