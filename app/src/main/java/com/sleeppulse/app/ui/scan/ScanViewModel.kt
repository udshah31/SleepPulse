package com.sleeppulse.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleeppulse.app.data.source.BleScanSource
import com.sleeppulse.app.data.source.BleTargetDeviceSink
import com.sleeppulse.app.data.source.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanSource: BleScanSource,
    private val targetDeviceSink: BleTargetDeviceSink,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _deviceSelected = Channel<ScannedDevice>(Channel.BUFFERED)
    val deviceSelected: Flow<ScannedDevice> = _deviceSelected.receiveAsFlow()

    fun onIntent(intent: ScanIntent) {
        when (intent) {
            ScanIntent.StartScan -> startScan()
            is ScanIntent.SelectDevice -> selectDevice(intent.device)
            ScanIntent.PermissionDenied ->
                _state.update { it.copy(error = "Bluetooth permissions are required to scan") }
        }
    }

    private fun startScan() {
        _state.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            scanSource.scan()
                .catch { e ->
                    _state.update { it.copy(isScanning = false, error = e.message ?: "Scan failed") }
                }
                .collect { devices ->
                    _state.update { it.copy(devices = devices) }
                }
        }
    }

    private fun selectDevice(device: ScannedDevice) {
        targetDeviceSink.setTargetDevice(device.address)
        _deviceSelected.trySend(device)
    }
}
