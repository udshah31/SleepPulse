package com.sleeppulse.app.ui.scan

import com.sleeppulse.app.data.source.ScannedDevice

sealed class ScanIntent {
    data object StartScan : ScanIntent()
    data class SelectDevice(val device: ScannedDevice) : ScanIntent()
    data object PermissionDenied : ScanIntent()
}

data class ScanState(
    val devices: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null,
)
