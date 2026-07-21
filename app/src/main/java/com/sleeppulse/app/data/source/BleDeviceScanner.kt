package com.sleeppulse.app.data.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ScannedDevice(val address: String, val name: String?)

/** Testable seam over [BleDeviceScanner] so [com.sleeppulse.app.ui.scan.ScanViewModel] can be unit-tested with a fake. */
interface BleScanSource {
    fun scan(): Flow<List<ScannedDevice>>
}

/**
 * Wraps [android.bluetooth.le.BluetoothLeScanner]. Results are unfiltered (no service-UUID
 * [android.bluetooth.le.ScanFilter]) so nearby devices show up during manual testing without
 * a real Heart Rate Service peripheral available.
 *
 * Callers must hold BLUETOOTH_SCAN/BLUETOOTH_CONNECT before calling [scan] — this class does
 * not check permissions itself and lets [android.bluetooth.le.BluetoothLeScanner.startScan]'s
 * [SecurityException] propagate as a flow failure if they're missing.
 */
class BleDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanSource {

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is unavailable or turned off"))
            return@callbackFlow
        }

        val devices = linkedMapOf<String, ScannedDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                devices[device.address] = ScannedDevice(address = device.address, name = device.name)
                trySend(devices.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed with error code $errorCode"))
            }
        }

        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose { scanner.stopScan(callback) }
    }
}
