package com.sleeppulse.app.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Note: the data-source toggle here currently only tracks the user's preference in-memory.
 * Wiring it to actually rebind [com.sleeppulse.app.data.source.SensorDataSource] to
 * [com.sleeppulse.app.data.source.BleSensorDataSource] at runtime is deferred to the
 * hardening pass alongside real BLE scanning — see README.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetDataSource -> _state.update { it.copy(dataSourceMode = intent.mode) }
            is SettingsIntent.SetTemperatureUnit -> _state.update { it.copy(temperatureUnit = intent.unit) }
            is SettingsIntent.SetSelectedBleDevice -> _state.update { it.copy(selectedBleDeviceLabel = intent.label) }
        }
    }
}
